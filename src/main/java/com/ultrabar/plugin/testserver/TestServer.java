package com.ultrabar.plugin.testserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionsResultPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.Heartbeat;
import com.ultrabar.plugin.model.HeartbeatAckPayload;
import com.ultrabar.plugin.model.Json;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.RegisterResultPayload;
import com.ultrabar.plugin.model.RequestIds;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal main-app stub for the plugin protocol.
 */
public class TestServer {
    public static void main(String[] args) throws Exception {
        int port = 39001;
        int closeAfterSeconds = 0;
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            closeAfterSeconds = Integer.parseInt(args[1]);
        }

        final ObjectMapper mapper = Json.mapper();
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);
        final AtomicReference<Channel> activeChannel = new AtomicReference<Channel>();
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new LineBasedFrameDecoder(10 * 1024 * 1024));
                            pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    System.out.println("Client connected: " + ctx.channel().remoteAddress());
                                    activeChannel.set(ctx.channel());
                                    super.channelActive(ctx);
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                    System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
                                    activeChannel.set(null);
                                    super.channelInactive(ctx);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
                                    Envelope envelope = mapper.readValue(msg, Envelope.class);
                                    System.out.println("[server] recv type=" + envelope.getType()
                                            + " reqId=" + envelope.getRequestId());
                                    handle(ctx, envelope, mapper, scheduler);
                                }

                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    cause.printStackTrace();
                                    ctx.close();
                                }
                            });
                        }
                    });

            ChannelFuture bind = bootstrap.bind(port).sync();
            System.out.println("TestServer listening on port " + port);

            if (closeAfterSeconds > 0) {
                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        Channel ch = activeChannel.get();
                        if (ch != null && ch.isActive()) {
                            System.out.println("[server] closing active connection to simulate crash");
                            ch.close();
                        }
                    }
                }, closeAfterSeconds, TimeUnit.SECONDS);
            }

            bind.channel().closeFuture().sync();
        } finally {
            scheduler.shutdownNow();
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    private static void handle(
            final ChannelHandlerContext ctx,
            Envelope envelope,
            final ObjectMapper mapper,
            ScheduledExecutorService scheduler) throws Exception {
        if (envelope.getType() == null) {
            System.out.println("[server] unknown type");
            return;
        }
        switch (envelope.getType()) {
            case REGISTER:
                RegisterResultPayload register = new RegisterResultPayload();
                register.success = true;
                register.sessionId = "sess-1";
                register.sessionToken = "token-1";
                register.heartbeat = new Heartbeat();
                register.heartbeat.interval = 5000;
                register.heartbeat.timeout = 15000;
                write(ctx, mapper, Envelope.of(MessageType.REGISTER_RESULT, envelope.getRequestId(), register));

                scheduler.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            CallPayload call = new CallPayload();
                            call.actionId = "music.play";
                            call.idempotencyKey = "call-play-1";
                            Map<String, Object> params = new HashMap<String, Object>();
                            params.put("deviceId", "speaker-001");
                            params.put("keyword", "song");
                            call.params = params;
                            write(ctx, mapper, Envelope.of(MessageType.CALL, RequestIds.next(), call));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, 1, TimeUnit.SECONDS);
                return;
            case ACTIONS:
                ActionsPayload actions = envelope.payloadAs(ActionsPayload.class);
                ActionsResultPayload ack = new ActionsResultPayload();
                ack.success = true;
                ack.receivedCount = (actions != null && actions.actions != null) ? actions.actions.size() : 0;
                write(ctx, mapper, Envelope.of(MessageType.ACTIONS_RESULT, envelope.getRequestId(), ack));

                DescribePayload describe = new DescribePayload("music.pause");
                write(ctx, mapper, Envelope.of(MessageType.DESCRIBE, RequestIds.next(), describe));
                return;
            case DESCRIBE_RESULT:
                GetOptionsPayload options = new GetOptionsPayload();
                options.actionId = "music.pause";
                options.parameterId = "deviceId";
                options.searchText = "sony";
                options.limit = 20;
                Map<String, Object> deps = new HashMap<String, Object>();
                deps.put("brand", "sony");
                options.params = deps;
                write(ctx, mapper, Envelope.of(MessageType.GET_OPTIONS, RequestIds.next(), options));
                return;
            case GET_OPTIONS_RESULT:
                System.out.println("[server] options returned");
                return;
            case HEARTBEAT:
                HeartbeatAckPayload heartbeatAck = new HeartbeatAckPayload();
                heartbeatAck.success = true;
                write(ctx, mapper, Envelope.of(MessageType.HEARTBEAT_ACK, envelope.getRequestId(), heartbeatAck));
                return;
            case CALL_RESULT:
                System.out.println("[server] call_result payload=" + mapper.writeValueAsString(envelope.getPayload()));
                return;
            case TASK_UPDATE:
                System.out.println("[server] task_update payload=" + mapper.writeValueAsString(envelope.getPayload()));
                return;
            default:
                System.out.println("[server] ignored type=" + envelope.getType());
        }
    }

    private static void write(ChannelHandlerContext ctx, ObjectMapper mapper, Envelope envelope) throws Exception {
        ctx.channel().writeAndFlush(mapper.writeValueAsString(envelope) + "\n");
    }
}
