package com.example.plugin;

import com.example.plugin.callback.ActionsCallback;
import com.example.plugin.callback.ActionsUpdateCallback;
import com.example.plugin.callback.RegisterCallback;
import com.example.plugin.model.Envelope;
import com.example.plugin.model.Protocol;
import com.example.plugin.model.RegisterPayload;
import com.example.plugin.model.RegisterResultPayload;
import com.example.plugin.model.ActionsPayload;
import com.example.plugin.model.ActionsAckPayload;
import com.example.plugin.model.CallPayload;
import com.example.plugin.model.ResultPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PluginClient - minimal Netty-based client for Ultrabar protocol.
 * Use model POJOs for payloads.
 */
public class PluginClient {

  private final String host;
  private final int port;
  private final ObjectMapper mapper = new ObjectMapper();

  private EventLoopGroup group;
  private Channel channel;
  private final AtomicLong idGen = new AtomicLong(1);

  private final ConcurrentMap<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();

  private final ConcurrentMap<String, RegisterCallback> registerCallbacks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ActionsCallback> actionsCallbacks = new ConcurrentHashMap<>();
  private volatile ActionsUpdateCallback actionsUpdateCallback;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private volatile boolean stopped = false;
  private final long reconnectIntervalMillis = 3000;

  public PluginClient() { this("127.0.0.1", 39001); }
  public PluginClient(String host, int port) { this.host = host; this.port = port; }

  public void start() { stopped = false; connect(); }
  public void stop() {
    stopped = true;
    if (channel != null) channel.close();
    if (group != null) group.shutdownGracefully();
    scheduler.shutdownNow();
  }

  private void connect() {
    group = new NioEventLoopGroup(1);
    Bootstrap b = new Bootstrap();
    b.group(group)
     .channel(NioSocketChannel.class)
     .option(ChannelOption.SO_KEEPALIVE, true)
     .handler(new ChannelInitializer<SocketChannel>() {
       @Override
       protected void initChannel(SocketChannel ch) {
         ChannelPipeline p = ch.pipeline();
         p.addLast(new LineBasedFrameDecoder(10 * 1024 * 1024));
         p.addLast(new StringDecoder(StandardCharsets.UTF_8));
         p.addLast(new StringEncoder(StandardCharsets.UTF_8));
         p.addLast(new ClientHandler());
       }
     });

    b.connect(host, port).addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        channel = future.channel();
        System.out.println("Connected to " + host + ":" + port);
      } else {
        System.err.println("Connect failed: " + future.cause() + ", retry in " + reconnectIntervalMillis + "ms");
        scheduleReconnect();
      }
    });
  }

  private void scheduleReconnect() { if (stopped) return; scheduler.schedule(this::connect, reconnectIntervalMillis, TimeUnit.MILLISECONDS); }

  private String nextRequestId() { return "req-" + idGen.getAndIncrement(); }

  // --- Public API ---
  public void register(RegisterPayload payload, RegisterCallback callback) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "register";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.payload = payload;

    registerCallbacks.put(reqId, callback);
    sendEnvelope(env);
  }

  public void sendActions(ActionsPayload payload, ActionsCallback callback) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "actions";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.payload = payload;
    actionsCallbacks.put(reqId, callback);
    sendEnvelope(env);
  }

  public CompletableFuture<Object> describe(String sessionId, String authBearer, String actionId) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "describe";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.sessionId = sessionId;
    env.auth = authBearer;
    env.payload = new com.example.plugin.model.DescribePayload(actionId);

    CompletableFuture<Object> fut = new CompletableFuture<>();
    pending.put(reqId, fut);
    sendEnvelope(env);
    return fut;
  }

  public CompletableFuture<Object> call(String sessionId, String authBearer, String action, java.util.Map<String, Object> params) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "call";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.sessionId = sessionId;
    env.auth = authBearer;

    CallPayload cp = new CallPayload();
    cp.action = action;
    cp.params = params;
    cp.idempotencyKey = reqId;
    env.payload = cp;

    CompletableFuture<Object> fut = new CompletableFuture<>();
    pending.put(reqId, fut);
    sendEnvelope(env);
    return fut;
  }

  public void setActionsUpdateCallback(ActionsUpdateCallback cb) { this.actionsUpdateCallback = cb; }

  private void sendEnvelope(Envelope env) {
    if (channel == null || !channel.isActive()) {
      String rid = env.requestId;
      if (rid != null) {
        CompletableFuture<Object> fut = pending.remove(rid);
        if (fut != null) fut.completeExceptionally(new IllegalStateException("Not connected"));
      }
      if ("register".equals(env.type)) {
        RegisterCallback rc = registerCallbacks.remove(env.requestId);
        if (rc != null) rc.onError(new IllegalStateException("Not connected"));
      }
      if ("actions".equals(env.type)) {
        ActionsCallback ac = actionsCallbacks.remove(env.requestId);
        if (ac != null) ac.onError(new IllegalStateException("Not connected"));
      }
      return;
    }
    try {
      String json = mapper.writeValueAsString(env);
      channel.writeAndFlush(json + "\n");
    } catch (Exception e) {
      String rid = env.requestId;
      if (rid != null) {
        CompletableFuture<Object> fut = pending.remove(rid);
        if (fut != null) fut.completeExceptionally(e);
        RegisterCallback rc = registerCallbacks.remove(rid);
        if (rc != null) rc.onError(e);
        ActionsCallback ac = actionsCallbacks.remove(rid);
        if (ac != null) ac.onError(e);
      }
    }
  }

  private class ClientHandler extends SimpleChannelInboundHandler<String> {
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      System.err.println("Channel inactive, scheduling reconnect");
      if (!stopped) scheduleReconnect();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
      try {
        JsonNode node = mapper.readTree(msg);
        String reqId = node.has("requestId") ? node.get("requestId").asText(null) : null;
        String type = node.has("type") ? node.get("type").asText(null) : null;
        JsonNode payload = node.has("payload") ? node.get("payload") : node;

        if (reqId != null && pending.containsKey(reqId)) {
          CompletableFuture<Object> fut = pending.remove(reqId);
          if (fut != null) {
            fut.complete(payload);
            return;
          }
        }

        if ("register_result".equals(type) && reqId != null) {
          RegisterCallback cb = registerCallbacks.remove(reqId);
          if (cb != null) {
            RegisterResultPayload rr = mapper.treeToValue(payload, RegisterResultPayload.class);
            if (Boolean.TRUE.equals(rr.success)) cb.onSuccess(payload);
            else cb.onError(new RuntimeException("register_result returned success=false"));
            return;
          }
        }

        if ("actions_ack".equals(type) && reqId != null) {
          ActionsCallback ac = actionsCallbacks.remove(reqId);
          if (ac != null) {
            ActionsAckPayload aap = mapper.treeToValue(payload, ActionsAckPayload.class);
            if (Boolean.TRUE.equals(aap.success)) ac.onSuccess(payload);
            else ac.onError(new RuntimeException("actions_ack success=false"));
            return;
          }
        }

        if ("actions_update".equals(type)) {
          if (actionsUpdateCallback != null) actionsUpdateCallback.onUpdate(payload);
          return;
        }

        System.out.println("Received message type=" + type + " requestId=" + reqId + " payload=" + payload.toString());
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      cause.printStackTrace();
      ctx.close();
    }
  }
}
