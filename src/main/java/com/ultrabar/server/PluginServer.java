package com.ultrabar.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.internal.RequestTable;
import com.ultrabar.plugin.model.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ultrabar plugin protocol server. Sessions are unique by {@code packageName}.
 */
public class PluginServer {
    private static final Logger log = LoggerFactory.getLogger(PluginServer.class);
    private static final int MAX_FRAME_BYTES = 10 * 1024 * 1024;
    private static final long CALL_TIMEOUT_MS = 30_000L;

    private final int port;
    private final ObjectMapper mapper;
    private final SessionRegistry sessions = new SessionRegistry();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            new DefaultThreadFactory("ultrabar-server-scheduler"));
    private final RequestTable pending = new RequestTable(scheduler);

    private final int heartbeatIntervalMs;
    private final int heartbeatTimeoutMs;
    private volatile PluginServerListener listener = new PluginServerListener() {
    };
    private volatile PluginRegisterHandler registerHandler = new DefaultRegisterHandler();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel bindChannel;
    private final String ip;

    public PluginServer() {
        this("127.0.0.1", 39001);
    }

    public PluginServer(String ip, int port) {
        this(ip, port, 5000, 15000);
    }

    public PluginServer(String ip, int port, int heartbeatIntervalMs, int heartbeatTimeoutMs) {
        this.port = port;
        this.ip = ip;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.heartbeatTimeoutMs = heartbeatTimeoutMs;
        this.mapper = Json.mapper();
    }

    public void setListener(PluginServerListener listener) {
        this.listener = listener == null ? new PluginServerListener() {
        } : listener;
    }

    public void setRegisterHandler(PluginRegisterHandler registerHandler) {
        this.registerHandler = registerHandler == null ? new DefaultRegisterHandler() : registerHandler;
    }

    public synchronized void start() throws InterruptedException {
        if (bindChannel != null) {
            return;
        }
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("ultrabar-server-boss"));
        workerGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("ultrabar-server-worker"));
        final PluginServer server = this;
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LineBasedFrameDecoder(MAX_FRAME_BYTES));
                        pipeline.addLast(new StringDecoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new StringEncoder(StandardCharsets.UTF_8));
                        pipeline.addLast(new ServerChannelHandler(server, mapper));
                    }
                });
        ChannelFuture future = bootstrap.bind(ip, port).sync();
        bindChannel = future.channel();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                expireIdleSessions();
            }
        }, heartbeatTimeoutMs, heartbeatTimeoutMs, TimeUnit.MILLISECONDS);
        log.info("plugin server listening on {}", port);
    }

    public synchronized void stop() {
        pending.failAll(new IllegalStateException("server stopped"));
        scheduler.shutdownNow();
        if (bindChannel != null) {
            bindChannel.close();
            bindChannel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }

    public Collection<PluginSession> sessions() {
        return sessions.all();
    }

    /**
     * Look up the live plugin session by registration {@code packageName}.
     * Returns {@code null} if that package is not online.
     */
    public PluginSession getSession(String packageName) {
        return sessions.byPackage(packageName);
    }

    /**
     * Find the plugin that registered {@code actionId} and invoke it.
     * If more than one plugin owns the same actionId, use
     * {@link #call(String, String, Map)} with packageName.
     */


    public CompletableFuture<CallResultPayload> call(String packageName, String actionId, Map<String, Object> params) {
        PluginSession session = sessions.byPackage(packageName);
        if (session == null || session.channel() == null || !session.channel().isActive()) {
            return failedFuture(new IllegalStateException("no active session for packageName=" + packageName));
        }
        if (!session.hasAction(actionId)) {
            return failedFuture(new IllegalStateException(
                    "package " + packageName + " has no actionId=" + actionId));
        }
        CallPayload payload = new CallPayload();
        payload.actionId = actionId;
        payload.params = params;
        payload.idempotencyKey = RequestIds.next();
        return request(session, MessageType.CALL, payload, CallResultPayload.class);
    }

    void onMessage(Channel channel, Envelope envelope) {
        if (envelope == null || envelope.getType() == null) {
            log.warn("inbound envelope missing type from {}", channel.remoteAddress());
            return;
        }
        try {
            dispatch(channel, envelope);
        } catch (Exception e) {
            log.error("failed to handle type={} from {}", envelope.getType(), channel.remoteAddress(), e);
        }
    }

    private void dispatch(Channel channel, Envelope envelope) {
        switch (envelope.getType()) {
            case REGISTER:
                handleRegister(channel, envelope);
                return;
            case ACTIONS:
                handleActions(channel, envelope);
                return;
            case HEARTBEAT:
                handleHeartbeat(channel, envelope);
                return;
            case CALL_RESULT:
            case DESCRIBE_RESULT:
            case GET_OPTIONS_RESULT:
            case HEARTBEAT_ACK:
                pending.complete(envelope.getRequestId(), envelope.getPayload());
                return;
            case TASK_UPDATE:
                handleTaskUpdate(channel, envelope);
                return;
            default:
                log.info("ignored plugin message type={} from {}", envelope.getType(), channel.remoteAddress());
        }
    }

    void onInactive(Channel channel) {
        PluginSession removed = sessions.removeIfBoundTo(channel);
        if (removed != null) {
            log.info("session gone packageName={} sessionId={}", removed.packageName(), removed.sessionId());
            listener.onUnregistered(removed);
        }
    }

    private void handleRegister(final Channel channel, final Envelope envelope) {
        final RegisterPayload payload;
        try {
            payload = envelope.payloadAs(RegisterPayload.class);
        } catch (Exception e) {
            writeRegisterFailure(channel, envelope, ErrorCodes.INVALID_PAYLOAD, e.getMessage());
            return;
        }
        if (payload == null || isBlank(payload.packageName)) {
            writeRegisterFailure(channel, envelope, ErrorCodes.MISSING_PACKAGE, "packageName is required");
            return;
        }
        scheduler.execute(new Runnable() {
            @Override
            public void run() {
                RegisterResultPayload result;
                try {
                    result = registerHandler.handleRegister(payload);
                } catch (Exception e) {
                    log.error("register handler failed packageName={}", payload.packageName, e);
                    result = failedRegister(ErrorCodes.HANDLER_EXCEPTION, e.getMessage());
                }
                if (!channel.isActive()) {
                    return;
                }
                completeRegister(channel, envelope, payload, result);
            }
        });
    }

    private void completeRegister(
            Channel channel,
            Envelope envelope,
            RegisterPayload payload,
            RegisterResultPayload result) {
        if (result == null) {
            result = failedRegister(ErrorCodes.HANDLER_EXCEPTION, "register handler returned null");
        }
        if (!Boolean.TRUE.equals(result.success)) {
            result.success = false;
            write(channel, Envelope.of(MessageType.REGISTER_RESULT, envelope.getRequestId(), result));
            log.info("register rejected packageName={}", payload.packageName);
            return;
        }
        fillRegisterDefaults(result);
        PluginSession session = new PluginSession(
                payload.packageName.trim(),
                result.sessionId,
                result.sessionToken,
                payload,
                channel, result.configServer.port);
        PluginSession replaced = sessions.put(session);
        if (replaced != null) {
            log.info("replaced session for packageName={}", session.packageName());
        }
        write(channel, Envelope.of(MessageType.REGISTER_RESULT, envelope.getRequestId(), result));
        log.info("registered packageName={} sessionId={}", session.packageName(), session.sessionId());
        listener.onRegistered(session);
    }

    private void fillRegisterDefaults(RegisterResultPayload result) {
        if (isBlank(result.sessionId)) {
            result.sessionId = "sess-" + UUID.randomUUID().toString();
        }
        if (isBlank(result.sessionToken)) {
            result.sessionToken = "token-" + UUID.randomUUID().toString();
        }
        if (result.heartbeat == null) {
            result.heartbeat = new Heartbeat();
        }
        if (result.heartbeat.interval == null) {
            result.heartbeat.interval = heartbeatIntervalMs;
        }
        if (result.heartbeat.timeout == null) {
            result.heartbeat.timeout = heartbeatTimeoutMs;
        }
    }

    private void writeRegisterFailure(Channel channel, Envelope envelope, String code, String message) {
        write(channel, Envelope.of(MessageType.REGISTER_RESULT, envelope.getRequestId(), failedRegister(code, message)));
    }

    private static RegisterResultPayload failedRegister(String code, String message) {
        RegisterResultPayload failed = new RegisterResultPayload();
        failed.success = false;
        failed.error = ErrorInfo.of(code, message, false, null);
        return failed;
    }

    private final class DefaultRegisterHandler implements PluginRegisterHandler {
        @Override
        public RegisterResultPayload handleRegister(RegisterPayload request) {
            RegisterResultPayload result = new RegisterResultPayload();
            result.success = true;
            return result;
        }
    }

    private void handleActions(Channel channel, Envelope envelope) {
        PluginSession session = requireSession(channel, envelope, MessageType.ACTIONS_RESULT);
        if (session == null) {
            return;
        }
        ActionsPayload payload = envelope.payloadAs(ActionsPayload.class);
        session.updateActions(payload == null ? null : payload.actions, payload == null ? null : payload.revision);
        ActionsResultPayload result = new ActionsResultPayload();
        result.success = true;
        result.receivedCount = session.actions().size();
        result.revision = session.revision();
        write(channel, Envelope.of(MessageType.ACTIONS_RESULT, envelope.getRequestId(), result)
                .withSession(session.sessionId(), session.sessionToken()));
        log.info("stored {} actions for packageName={}", result.receivedCount, session.packageName());
        listener.onActionsUpdated(session);
    }

    private void handleHeartbeat(Channel channel, Envelope envelope) {
        PluginSession session = requireSession(channel, envelope, MessageType.HEARTBEAT_ACK);
        if (session == null) {
            return;
        }
        session.touch();
        HeartbeatAckPayload ack = new HeartbeatAckPayload();
        ack.success = true;
        write(channel, Envelope.of(MessageType.HEARTBEAT_ACK, envelope.getRequestId(), ack)
                .withSession(session.sessionId(), session.sessionToken()));
    }

    private void handleTaskUpdate(Channel channel, Envelope envelope) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            log.warn("task_update without session from {}", channel.remoteAddress());
            return;
        }
        session.touch();
        listener.onTaskUpdate(session, envelope.payloadAs(TaskUpdatePayload.class));
    }

    private PluginSession requireSession(Channel channel, Envelope envelope, MessageType replyType) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            writeError(channel, envelope, replyType, ErrorCodes.NO_SESSION, "no session for this connection");
            return null;
        }
        if (envelope.getAuth() != null && !session.sessionToken().equals(envelope.getAuth())) {
            writeError(channel, envelope, replyType, ErrorCodes.AUTH_FAILED, "session token mismatch");
            return null;
        }
        if (envelope.getSessionId() != null && !session.sessionId().equals(envelope.getSessionId())) {
            writeError(channel, envelope, replyType, ErrorCodes.AUTH_FAILED, "sessionId mismatch");
            return null;
        }
        session.touch();
        return session;
    }

    private void writeError(Channel channel, Envelope request, MessageType replyType, String code, String message) {
        Payload reply;
        if (replyType == MessageType.ACTIONS_RESULT) {
            ActionsResultPayload payload = new ActionsResultPayload();
            payload.success = false;
            payload.error = ErrorInfo.of(code, message, false, null);
            reply = payload;
        } else if (replyType == MessageType.HEARTBEAT_ACK) {
            HeartbeatAckPayload payload = new HeartbeatAckPayload();
            payload.success = false;
            payload.error = ErrorInfo.of(code, message, false, null);
            reply = payload;
        } else {
            RegisterResultPayload payload = new RegisterResultPayload();
            payload.success = false;
            payload.error = ErrorInfo.of(code, message, false, null);
            reply = payload;
        }
        write(channel, Envelope.of(replyType, request.getRequestId(), reply));
    }

    private <T> CompletableFuture<T> request(
            PluginSession session, MessageType type, Payload payload, Class<T> responseType) {
        Channel channel = session.channel();
        String requestId = RequestIds.next();
        CompletableFuture<T> future = pending.register(requestId, responseType, CALL_TIMEOUT_MS);
        try {
            write(channel, Envelope.of(type, requestId, payload)
                    .withSession(session.sessionId(), session.sessionToken()));
            return future;
        } catch (RuntimeException e) {
            pending.fail(requestId, e);
            return failedFuture(e);
        }
    }

    private void write(Channel channel, Envelope envelope) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("channel is not active");
        }
        try {
            channel.writeAndFlush(mapper.writeValueAsString(envelope) + "\n");
        } catch (Exception e) {
            throw new IllegalStateException("failed to write " + envelope.getType(), e);
        }
    }

    private void expireIdleSessions() {
        long now = System.currentTimeMillis();
        for (PluginSession session : sessions.all()) {
            if (now - session.lastSeenMillis() > heartbeatTimeoutMs) {
                Channel channel = session.channel();
                log.warn("heartbeat timeout packageName={}", session.packageName());
                if (channel != null) {
                    channel.close();
                }
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
    }
}
