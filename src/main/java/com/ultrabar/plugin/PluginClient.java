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
 * Behavior:
 * - Configuration-driven: setRegisterConfig/setActionsConfig before startAsync();
 * - SDK auto-registers and auto-sends actions after start and connection established;
 * - updateActions(...) triggers a forced refresh when actions change at runtime;
 * - Single PluginListener receives global events (register/actions failures, actions ack/update, describe results, incoming call).
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

  /**
   * Configure register/actions that SDK should automatically send after connection.
   * Call these before startAsync().
   */
  public void setRegisterConfig(RegisterPayload rp) { this.registerConfig = rp; }
  public void setActionsConfig(ActionsPayload ap) { this.actionsConfig = ap; }

  /**
   * Update actions at runtime and force a refresh/send of latest actions if already registered.
   */
  public void updateActions(ActionsPayload ap) {
    this.actionsConfig = ap;
    // If already registered, send immediately
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

  /**
   * Start connecting asynchronously. SDK will auto-register and send actions when connected.
   */
  public CompletableFuture<Void> startAsync() {
    stopped = false;
    CompletableFuture<Void> existing = connectFutureRef.get();
    if (existing != null && !existing.isDone()) return existing;

    CompletableFuture<Void> future = new CompletableFuture<>();
    if (!connectFutureRef.compareAndSet(existing, future)) {
      CompletableFuture<Void> cur = connectFutureRef.get();
      return (cur != null) ? cur : future;
    }
    // Trigger connect attempts
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

    final CompletableFuture<Void> connectionFuture = connectFutureRef.get();
    b.connect(host, port).addListener((ChannelFutureListener) future -> {
      if (future.isSuccess()) {
        channel = future.channel();
        System.out.println("Connected to " + host + ":" + port);
        // complete the start future if present
        CompletableFuture<Void> cf = connectFutureRef.getAndSet(null);
        if (cf != null && !cf.isDone()) cf.complete(null);
        // on connect, attempt to auto-register if configured
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

  // --- Public API ---
  /**
   * Make an outbound describe request. Returns a future that completes with the payload DescribeResultPayload.
   */
  public CompletableFuture<DescribeResultPayload> describe(String sessionId, String authBearer, String actionId) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "describe";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.sessionId = (sessionId != null) ? sessionId : savedSessionId;
    env.auth = (authBearer != null) ? authBearer : savedSessionToken;
    env.payload = new DescribePayload(actionId);

    CompletableFuture<DescribeResultPayload> fut = new CompletableFuture<>();
    pending.put(reqId, (CompletableFuture<Object>)(CompletableFuture<?>)fut);

    // attach plugin listener callbacks for describe
    if (pluginListener != null) {
      fut.thenAccept(resp -> submitCallback(() -> pluginListener.onDescribeSuccess(resp))).exceptionally(ex -> { submitCallback(() -> pluginListener.onDescribeError(ex)); return null; });
    }

    sendEnvelope(env);
    return fut;
  }

  /**
   * Make an outbound call to the main App. Returns a future that completes when a reply arrives.
   */
  public CompletableFuture<Object> call(String sessionId, String authBearer, String action, java.util.Map<String, Object> params) {
    String reqId = nextRequestId();
    Envelope env = new Envelope();
    env.type = "call";
    env.requestId = reqId;
    env.timestamp = Instant.now().toString();
    env.protocol = new Protocol("ultrabar.plugin", 1);
    env.sessionId = (sessionId != null) ? sessionId : savedSessionId;
    env.auth = (authBearer != null) ? authBearer : savedSessionToken;

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

  /**
   * Register a handler for incoming `call` requests from the server.
   */
  public void setCallHandler(CallHandler handler) { this.callHandler = handler; }

  public void setActionsUpdateCallback(ActionsUpdateCallback cb) { this.actionsUpdateCallback = cb; }

  // --- Internal send helpers ---
  private void sendEnvelope(Envelope env) {
    // If not connected yet, queue the envelope to be sent after connection
    if (channel == null || !channel.isActive()) {
      pendingEnvelopes.add(env);
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

  private void drainPendingEnvelopes() {
    Envelope env;
    while ((env = pendingEnvelopes.poll()) != null) {
      sendEnvelope(env);
    }
  }

  private void onConnected() {
    // When connection established, cancel any previous heartbeat and clear saved session (we'll re-register)
    cancelHeartbeat();
    savedSessionId = null;
    savedSessionToken = null;

    // first, send any queued envelopes (so describe/call queued before start will be sent)
    drainPendingEnvelopes();

    // If we have a registerConfig configured, attempt register -> then send actions if configured
    if (registerConfig != null) {
      attemptAutoRegister();
    }
  }

  private void attemptAutoRegister() {
    RegisterPayload payload = registerConfig;
    if (payload == null) return;

    // Use internal callback to save session and schedule heartbeat, then send actions if config exists
    this.sendRegisterInternal(payload, new RegisterCallback() {
      @Override
      public void onSuccess(JsonNode registerResultPayload) {
        try {
          RegisterResultPayload rr = mapper.treeToValue(registerResultPayload, RegisterResultPayload.class);
          if (rr != null) {
            savedSessionId = rr.sessionId;
            savedSessionToken = rr.sessionToken;
            if (rr.heartbeat != null && rr.heartbeat.interval != null) {
              heartbeatIntervalMillis = rr.heartbeat.interval;
            }
            scheduleHeartbeat();
            if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterSuccess(rr));
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        // send actions if configured
        if (actionsConfig != null) {
          sendActionsInternal(actionsConfig, new ActionsCallback() {
            @Override
            public void onSuccess(JsonNode ackPayload) {
              try {
                ActionsAckPayload aap = mapper.treeToValue(ackPayload, ActionsAckPayload.class);
                System.out.println("Auto actions ack: " + aap);
                if (pluginListener != null) submitCallback(() -> pluginListener.onActionsAck(aap));
                if (actionsUpdateCallback != null) actionsUpdateCallback.onUpdate(ackPayload);
              } catch (Exception e) {
                if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(e));
              }
            }

            @Override
            public void onError(Throwable t) {
              System.err.println("Auto actions failed: " + t.getMessage());
              if (pluginListener != null) submitCallback(() -> pluginListener.onActionsFailed(t));
            }
          });
        }
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Auto register failed: " + t.getMessage());
        if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterFailed(t));
        // schedule another attempt
        scheduler.schedule(PluginClient.this::attemptAutoRegister, baseReconnectIntervalMillis, TimeUnit.MILLISECONDS);
      }
    });
  }

  // Internal register (not exposed). Used by auto-register flow.
  private void sendRegisterInternal(RegisterPayload payload, RegisterCallback callback) {
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

  // Internal sendActions (not exposed). Used by auto-actions flow.
  private void sendActionsInternal(ActionsPayload payload, ActionsCallback callback) {
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

  private void scheduleHeartbeat() {
    cancelHeartbeat();
    if (heartbeatIntervalMillis <= 0) return;
    heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
      try {
        if (channel == null || !channel.isActive()) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", savedSessionId);
        payload.put("status", "alive");
        Envelope env = new Envelope();
        env.type = "heartbeat";
        env.requestId = nextRequestId();
        env.timestamp = Instant.now().toString();
        env.protocol = new Protocol("ultrabar.plugin", 1);
        env.payload = payload;
        sendEnvelope(env);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
  }

  private void cancelHeartbeat() {
    if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
      heartbeatTask.cancel(true);
      heartbeatTask = null;
    }
  }

  private class ClientHandler extends SimpleChannelInboundHandler<String> {
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
      System.err.println("Channel inactive, scheduling reconnect");
      cancelHeartbeat();
      savedSessionId = null;
      savedSessionToken = null;
      if (!stopped) scheduleReconnect();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
      try {
        JsonNode node = mapper.readTree(msg);
        String reqId = node.has("requestId") ? node.get("requestId").asText(null) : null;
        String type = node.has("type") ? node.get("type").asText(null) : null;
        JsonNode payload = node.has("payload") ? node.get("payload") : node;

        // If there's a pending future for this requestId, complete it
        if (reqId != null && pending.containsKey(reqId)) {
          CompletableFuture<Object> fut = pending.remove(reqId);
          if (fut != null) {
            // try to convert to known result types if needed
            if ("describe_result".equals(type)) {
              try {
                DescribeResultPayload dr = mapper.treeToValue(payload, DescribeResultPayload.class);
                ((CompletableFuture<DescribeResultPayload>)(CompletableFuture<?>)fut).complete(dr);
                return;
              } catch (Exception e) {
                fut.complete(payload);
                return;
              }
            }
            fut.complete(payload);
            return;
          }
        }

        // register_result handling
        if ("register_result".equals(type) && reqId != null) {
          RegisterCallback cb = registerCallbacks.remove(reqId);
          if (cb != null) {
            RegisterResultPayload rr = mapper.treeToValue(payload, RegisterResultPayload.class);
            if (Boolean.TRUE.equals(rr.success)) {
              savedSessionId = rr.sessionId;
              savedSessionToken = rr.sessionToken;
              if (rr.heartbeat != null && rr.heartbeat.interval != null) {
                heartbeatIntervalMillis = rr.heartbeat.interval;
              }
              scheduleHeartbeat();
              cb.onSuccess(payload);
              if (pluginListener != null) submitCallback(() -> pluginListener.onRegisterSuccess(rr));
            } else {
              cb.onError(new RuntimeException("register_result returned success=false"));
            }
            return;
          }
        }

        // actions_ack handling
        if ("actions_ack".equals(type) && reqId != null) {
          ActionsCallback ac = actionsCallbacks.remove(reqId);
          if (ac != null) {
            ActionsAckPayload aap = mapper.treeToValue(payload, ActionsAckPayload.class);
            if (Boolean.TRUE.equals(aap.success)) ac.onSuccess(payload);
            else ac.onError(new RuntimeException("actions_ack success=false"));
            // notify global listener
            if (pluginListener != null) submitCallback(() -> pluginListener.onActionsAck(aap));
            return;
          }
        }

        // actions_update push
        if ("actions_update".equals(type)) {
          if (actionsUpdateCallback != null) {
            actionsUpdateCallback.onUpdate(payload);
          }
          try {
            ActionsPayload ap = mapper.treeToValue(payload, ActionsPayload.class);
            if (pluginListener != null) submitCallback(() -> pluginListener.onActionsUpdate(ap));
            return;
          } catch (Exception ex) {
            // fallback: ignore mapping error
          }
        }

        // incoming describe from server -> dispatch to PluginListener
        if ("describe".equals(type)) {
          DescribePayload dp = mapper.treeToValue(payload, DescribePayload.class);
          DescribeResponder responder = new DescribeResponder(ctx.channel(), reqId, mapper);
          if (pluginListener != null) {
            try {
              submitCallback(() -> pluginListener.onDescribe(dp, responder));
            } catch (Exception ex) {
              responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null);
            }
            return;
          } else {
            responder.sendError("NO_HANDLER", "No PluginListener registered", false, null);
            return;
          }
        }

        // incoming call from server -> dispatch to PluginListener or CallHandler
        if ("call".equals(type)) {
          CallPayload cp = mapper.treeToValue(payload, CallPayload.class);
          CallResponder responder = new CallResponder(ctx.channel(), reqId, mapper);
          if (pluginListener != null) {
            try {
              submitCallback(() -> pluginListener.onCall(cp, responder));
            } catch (Exception ex) {
              responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null);
            }
            return;
          }
          if (callHandler != null) {
            try {
              callHandler.onCall(cp, responder);
            } catch (Exception ex) {
              responder.sendError("HANDLER_EXCEPTION", ex.getMessage(), false, null);
            }
            return;
          } else {
            responder.sendError("NO_HANDLER", "No CallHandler registered", false, null);
            return;
          }
        }

        // fallback: log
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

  private void submitCallback(Runnable r) {
    try {
      callbackExecutor.submit(r);
    } catch (RejectedExecutionException e) {
      // fallback to calling directly
      r.run();
    }
  }
}
