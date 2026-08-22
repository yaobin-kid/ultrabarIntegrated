package com.ultrabar.plugin.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One EventLoopGroup for the client lifetime of a start/stop cycle.
 * Reconnects without allocating a new group.
 */
public final class LineConnection {
    private static final Logger log = LoggerFactory.getLogger(LineConnection.class);
    private static final long RECONNECT_INTERVAL_MS = 3_000L;
    private static final int MAX_FRAME_BYTES = 10 * 1024 * 1024;

    public interface Listener {
        void onConnected(Channel channel);

        void onDisconnected();

        void onMessage(Channel channel, String line);
    }

    private final String host;
    private final int port;
    private final ObjectMapper mapper;
    private final Listener listener;

    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicReference<Channel> channelRef = new AtomicReference<Channel>();
    private final AtomicReference<CompletableFuture<Void>> startFuture =
            new AtomicReference<CompletableFuture<Void>>();
    private final Queue<String> outboundQueue = new ConcurrentLinkedQueue<String>();

    private volatile EventLoopGroup group;

    public LineConnection(String host, int port, ObjectMapper mapper, Listener listener) {
        this.host = host;
        this.port = port;
        this.mapper = mapper;
        this.listener = listener;
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> existing = startFuture.get();
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Void> created = new CompletableFuture<Void>();
        if (!startFuture.compareAndSet(null, created)) {
            return startFuture.get();
        }
        stopped.set(false);
        group = new NioEventLoopGroup(1, new DefaultThreadFactory("ultrabar-plugin-io"));
        connect();
        return created;
    }

    public void stop() {
        stopped.set(true);
        CompletableFuture<Void> future = startFuture.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new IllegalStateException("client stopped"));
        }
        Channel channel = channelRef.getAndSet(null);
        if (channel != null) {
            channel.close();
        }
        EventLoopGroup current = group;
        group = null;
        if (current != null) {
            current.shutdownGracefully();
        }
        outboundQueue.clear();
        connecting.set(false);
    }

    boolean isActive() {
        Channel channel = channelRef.get();
        return channel != null && channel.isActive();
    }

    void sendJson(Object envelope) {
        String json;
        try {
            json = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("failed to serialize envelope", e);
        }
        Channel channel = channelRef.get();
        if (channel == null || !channel.isActive()) {
            outboundQueue.add(json);
            return;
        }
        channel.writeAndFlush(json + "\n");
    }

    private void connect() {
        if (stopped.get()) {
            return;
        }
        Channel existing = channelRef.get();
        if (existing != null && existing.isActive()) {
            return;
        }
        EventLoopGroup currentGroup = group;
        if (currentGroup == null || currentGroup.isShuttingDown() || currentGroup.isShutdown()) {
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(currentGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LineBasedFrameDecoder(MAX_FRAME_BYTES));
                        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new InboundHandler());
                    }
                });

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            connecting.set(false);
            if (stopped.get()) {
                if (future.isSuccess()) {
                    future.channel().close();
                }
                return;
            }
            if (future.isSuccess()) {
                log.info("connected to {}:{}", host, port);
                Channel channel = future.channel();
                channelRef.set(channel);
                drainQueue(channel);
                CompletableFuture<Void> start = startFuture.get();
                if (start != null && !start.isDone()) {
                    start.complete(null);
                }
                listener.onConnected(channel);
            } else {
                log.warn("connect to {}:{} failed: {}", host, port, future.cause().toString());
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (stopped.get()) {
            return;
        }
        EventLoopGroup currentGroup = group;
        if (currentGroup == null || currentGroup.isShuttingDown() || currentGroup.isShutdown()) {
            return;
        }
        currentGroup.schedule(new Runnable() {
            @Override
            public void run() {
                connect();
            }
        }, RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void drainQueue(Channel channel) {
        String json;
        while ((json = outboundQueue.poll()) != null) {
            channel.writeAndFlush(json + "\n");
        }
    }

    private void onInactive(Channel channel) {
        channelRef.compareAndSet(channel, null);
        if (stopped.get()) {
            return;
        }
        log.warn("connection to {}:{} closed, reconnecting", host, port);
        listener.onDisconnected();
        scheduleReconnect();
    }

    private final class InboundHandler extends SimpleChannelInboundHandler<String> {
        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            onInactive(ctx.channel());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            listener.onMessage(ctx.channel(), msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("connection error", cause);
            ctx.close();
        }
    }
}
