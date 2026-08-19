package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.callback.ActionsCallback;
import com.ultrabar.plugin.callback.ActionsUpdateCallback;
import com.ultrabar.plugin.callback.CallHandler;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.RegisterCallback;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.Protocol;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionsAckPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.ResultPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.DescribeResultPayload;
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
import java.util.Queue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PluginClient - Netty-based client for Ultrabar protocol.
 *
 * Refactored ClientHandler.channelRead0 for clarity and maintainability.
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
  private volatile CallHandler callHandler;

  // single global listener
  private volatile PluginListener pluginListener;

  // stored configuration for automatic register / actions
  private volatile RegisterPayload registerConfig;
  private volatile ActionsPayload actionsConfig;

  // saved session info from register_result
  private volatile String savedSessionId;
  private volatile String savedSessionToken;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "plugin-client-scheduler"));
  private final ExecutorService callbackExecutor = Executors.newCachedThreadPool(r -> new Thread(r, "plugin-client-callback"));
  private volatile boolean stopped = false;
  private final long baseReconnectIntervalMillis = 3000;

  // heartbeat task
  private ScheduledFuture<?> heartbeatTask;
  private volatile long heartbeatIntervalMillis = 30000; // default

  // queued envelopes before connection is established
  private final Queue<Envelope> pendingEnvelopes = new ConcurrentLinkedQueue<>();

  // connection future (completes when a TCP connection is established)
  private final AtomicReference<CompletableFuture<Void>> connectFutureRef = new AtomicReference<>();

  public PluginClient() { this("127.0.0.1", 39001); }
  public PluginClient(String host, int port) { this.host = host; this.port = port; }

  public void setRegisterConfig(RegisterPayload rp) { this.registerConfig = rp; }
  public void setActionsConfig(ActionsPayload ap) { this.actionsConfig = ap; }

  public void updateActions(ActionsPayload ap) {
    this.actionsConfig = ap;
    if (savedSessionId != null && channel != null && channel.isActive()) {
      sendActionsInternal(ap, new ActionsCallback() {
        @Override
        public void onSuccess(JsonNode ackPayload) {
          try {
            ActionsAckPayload aap = mapper.treeToValue(ackPayload, ActionsAckPayload.class);
            if (pluginListener != null) submitCallback(() -> pluginListener.onActionsAck(aap));
            if (actionsUpdateCallback != null) actionsUpdateCallback.onUpdate(ackPayload);
          } catch (Exception e) {
            if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(e));
          }
        }

        @Override
        public void onError(Throwable t) {
          if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(t));
        }
      });
    }
  }

  public void setPluginListener(PluginListener listener) { this.pluginListener = listener; }

  public CompletableFuture<Void> startAsync() {
    stopped = false;
    CompletableFuture<Void> existing = connectFutureRef.get();
    if (existing != null && !existing.isDone()) return existing;

    CompletableFuture<Void> future = new CompletableFuture<>();
    if (!connectFutureRef.compareAndSet(existing, future)) {
      CompletableFuture<Void> cur = connectFutureRef.get();
      return (cur != null) ? cur : future;
    }
    connect();
    return future;
  }

  public void stop() {
    stopped = true;
    CompletableFuture<Void> cf = connectFutureRef.getAndSet(null);
    if (cf != null && !cf.isDone()) cf.completeExceptionally(new IllegalStateException("Client stopped"));
    if (channel != null) channel.close();
    if (group != null) group.shutdownGracefully();
    cancelHeartbeat();
    scheduler.shutdownNow();
    callbackExecutor.shutdownNow();
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
        CompletableFuture<Void> cf = connectFutureRef.getAndSet(null);
        if (cf != null && !cf.isDone()) cf.complete(null);
        onConnected();
      } else {
        System.err.println("Connect failed: " + future.cause() + ", retry in " + baseReconnectIntervalMillis + "ms");
        scheduleReconnect();
      }
    });
  }

  private void scheduleReconnect() {
    if (stopped) return;
    scheduler.schedule(this::connect, baseReconnectIntervalMillis, TimeUnit.MILLISECONDS);
  }

  private String nextRequestId() { return "req-" + idGen.getAndIncrement(); }

  // --- Internal send helpers ---
  private void sendEnvelope(Envelope env) {
    if (channel == null || !channel.isActive()) { pendingEnvelopes.add(env); return; }
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

  private void drainPendingEnvelopes() {
    Envelope env;
    while ((env = pendingEnvelopes.poll()) != null) sendEnvelope(env);
  }

  private void onConnected() {
    cancelHeartbeat();
    savedSessionId = null;
    savedSessionToken = null;
    drainPendingEnvelopes();
    if (registerConfig != null) attemptAutoRegister();
  }

  private void attemptAutoRegister() {
    RegisterPayload payload = registerConfig; if (payload == null) return;
    sendRegisterInternal(payload, new RegisterCallback() {
      @Override public void onSuccess(JsonNode registerResultPayload) {
        try {
          RegisterResultPayload rr = mapper.treeToValue(registerResultPayload, RegisterResultPayload.class);
          if (rr != null) {
            savedSessionId = rr.sessionId; savedSessionToken = rr.sessionToken;
            if (rr.heartbeat != null && rr.heartbeat.interval != null) heartbeatIntervalMillis = rr.heartbeat.interval;
            scheduleHeartbeat();
            if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterSuccess(rr));
          }
        } catch (Exception e) { e.printStackTrace(); }
        if (actionsConfig != null) {
          sendActionsInternal(actionsConfig, new ActionsCallback() {
            @Override public void onSuccess(JsonNode ackPayload) {
              try {
                ActionsAckPayload aap = mapper.treeToValue(ackPayload, ActionsAckPayload.class);
                if (pluginListener != null) submitCallback(() -> pluginListener.onActionsAck(aap));
                if (actionsUpdateCallback != null) actionsUpdateCallback.onUpdate(ackPayload);
              } catch (Exception e) { if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(e)); }
            }
            @Override public void onError(Throwable t) { if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(t)); }
          });
        }
      }
      @Override public void onError(Throwable t) {
        if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterFailed(t));
        scheduler.schedule(PluginClient.this::attemptAutoRegister, baseReconnectIntervalMillis, TimeUnit.MILLISECONDS);
      }
    });
  }

  private void sendRegisterInternal(RegisterPayload payload, RegisterCallback callback) {
    String reqId = nextRequestId(); Envelope env = new Envelope(); env.type = "register"; env.requestId = reqId; env.timestamp = Instant.now().toString(); env.protocol = new Protocol("ultrabar.plugin", 1); env.payload = payload; registerCallbacks.put(reqId, callback); sendEnvelope(env);
  }

  private void sendActionsInternal(ActionsPayload payload, ActionsCallback callback) {
    String reqId = nextRequestId(); Envelope env = new Envelope(); env.type = "actions"; env.requestId = reqId; env.timestamp = Instant.now().toString(); env.protocol = new Protocol("ultrabar.plugin", 1); env.payload = payload; actionsCallbacks.put(reqId, callback); sendEnvelope(env);
  }

  private void scheduleHeartbeat() {
    cancelHeartbeat(); if (heartbeatIntervalMillis <= 0) return;
    heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
      try {
        if (channel == null || !channel.isActive()) return;
        Map<String, Object> payload = new HashMap<>(); payload.put("sessionId", savedSessionId); payload.put("status", "alive"); Envelope env = new Envelope(); env.type = "heartbeat"; env.requestId = nextRequestId(); env.timestamp = Instant.now().toString(); env.protocol = new Protocol("ultrabar.plugin", 1); env.payload = payload; sendEnvelope(env);
      } catch (Exception e) { e.printStackTrace(); }
    }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
  }

  private void cancelHeartbeat() { if (heartbeatTask != null && !heartbeatTask.isCancelled()) { heartbeatTask.cancel(true); heartbeatTask = null; } }

  private class ClientHandler extends SimpleChannelInboundHandler<String> {
    @Override public void channelInactive(ChannelHandlerContext ctx) {
      System.err.println("Channel inactive, scheduling reconnect"); cancelHeartbeat(); savedSessionId = null; savedSessionToken = null; if (!stopped) scheduleReconnect(); }

    @Override protected void channelRead0(ChannelHandlerContext ctx, String msg) {
      try {
        JsonNode node = mapper.readTree(msg);
        String reqId = node.has("requestId") ? node.get("requestId").asText(null) : null;
        String type = node.has("type") ? node.get("type").asText(null) : null;
        JsonNode payload = node.has("payload") ? node.get("payload") : node;

        // First attempt: complete any pending request
        if (reqId != null && tryCompletePending(reqId, type, payload)) return;

        // Dispatch by type
        switch (type) {
          case "register_result":
            handleRegisterResult(reqId, payload);
            return;
          case "actions_ack":
            handleActionsAck(reqId, payload);
            return;
          case "actions_update":
            handleActionsUpdate(payload);
            return;
          case "describe":
            handleDescribe(ctx, reqId, payload);
            return;
          case "call":
            handleCall(ctx, reqId, payload);
            return;
          default:
            System.out.println("Received message type=" + type + " requestId=" + reqId + " payload=" + payload.toString());
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    private boolean tryCompletePending(String reqId, String type, JsonNode payload) {
      if (reqId == null) return false;
      CompletableFuture<Object> fut = pending.remove(reqId);
      if (fut == null) return false;
      if ("describe_result".equals(type)) {
        try {
          DescribeResultPayload dr = mapper.treeToValue(payload, DescribeResultPayload.class);
          @SuppressWarnings("unchecked")
          CompletableFuture<DescribeResultPayload> df = (CompletableFuture<DescribeResultPayload>)(CompletableFuture<?>)fut;
          df.complete(dr);
        } catch (Exception e) {
          fut.complete(payload);
        }
      } else {
        fut.complete(payload);
      }
      return true;
    }

    private void handleRegisterResult(String reqId, JsonNode payload) {
      if (reqId == null) return;
      RegisterCallback cb = registerCallbacks.remove(reqId);
      if (cb == null) return;
      try {
        RegisterResultPayload rr = mapper.treeToValue(payload, RegisterResultPayload.class);
        if (Boolean.TRUE.equals(rr.success)) {
          savedSessionId = rr.sessionId; savedSessionToken = rr.sessionToken; if (rr.heartbeat != null && rr.heartbeat.interval != null) heartbeatIntervalMillis = rr.heartbeat.interval; scheduleHeartbeat(); cb.onSuccess(payload); if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterSuccess(rr));
        } else {
          cb.onError(new RuntimeException("register_result returned success=false"));
        }
      } catch (Exception e) { cb.onError(e); }
    }

    private void handleActionsAck(String reqId, JsonNode payload) {
      if (reqId == null) return;
      ActionsCallback ac = actionsCallbacks.remove(reqId);
      if (ac == null) return;
      try {
        ActionsAckPayload aap = mapper.treeToValue(payload, ActionsAckPayload.class);
        if (Boolean.TRUE.equals(aap.success)) ac.onSuccess(payload); else ac.onError(new RuntimeException("actions_ack success=false"));
        if (pluginListener != null) submitCallback(() -> pluginListener.onActionsAck(aap));
      } catch (Exception e) { ac.onError(e); }
    }

    private void handleActionsUpdate(JsonNode payload) {
      if (actionsUpdateCallback != null) actionsUpdateCallback.onUpdate(payload);
      try {
        ActionsPayload ap = mapper.treeToValue(payload, ActionsPayload.class);
        if (pluginListener != null) submitCallback(() -> pluginListener.onActionsUpdate(ap));
      } catch (Exception ex) { /* ignore mapping error */ }
    }

    private void handleDescribe(ChannelHandlerContext ctx, String reqId, JsonNode payload) {
      try {
        DescribePayload dp = mapper.treeToValue(payload, DescribePayload.class);
        DescribeResponder responder = new DescribeResponder(ctx.channel(), reqId, mapper);
        if (pluginListener != null) {
          submitCallback(() -> {
            try { pluginListener.onDescribe(dp, responder); } catch (Exception ex) { responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null); }
          });
        } else {
          responder.sendError("NO_HANDLER", "No PluginListener registered", false, null);
        }
      } catch (Exception e) {
        // if parsing failed, respond with error so server doesn't hang
        DescribeResponder responder = new DescribeResponder(ctx.channel(), reqId, mapper);
        responder.sendError("INVALID_PAYLOAD", e.getMessage(), false, null);
      }
    }

    private void handleCall(ChannelHandlerContext ctx, String reqId, JsonNode payload) {
      try {
        CallPayload cp = mapper.treeToValue(payload, CallPayload.class);
        CallResponder responder = new CallResponder(ctx.channel(), reqId, mapper);
        if (pluginListener != null) {
          submitCallback(() -> { try { pluginListener.onCall(cp, responder); } catch (Exception ex) { responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null); } });
          return;
        }
        if (callHandler != null) {
          try { callHandler.onCall(cp, responder); } catch (Exception ex) { responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null); }
          return;
        }
        responder.sendError("NO_HANDLER", "No CallHandler registered", false, null);
      } catch (Exception e) {
        CallResponder responder = new CallResponder(ctx.channel(), reqId, mapper);
        responder.sendError("INVALID_PAYLOAD", e.getMessage(), false, null);
      }
    }

    @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) { cause.printStackTrace(); ctx.close(); }
  }

  private void submitCallback(Runnable r) {
    try { callbackExecutor.submit(r); } catch (RejectedExecutionException e) { r.run(); }
  }
}
