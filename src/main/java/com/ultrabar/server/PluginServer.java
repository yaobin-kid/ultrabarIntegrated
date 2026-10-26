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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Ultrabar plugin protocol server. Sessions are unique by {@code packageName}.
 */
public class PluginServer {
    private static final Logger log = LoggerFactory.getLogger(PluginServer.class);
    private static final int MAX_FRAME_BYTES = 10 * 1024 * 1024;
    private static final long CALL_TIMEOUT_MS = 30_000L;
    private static final long MODULE_EVENT_TIMEOUT_MS = 1_000L;

    /**
     * 模块事件分发给单个插件的最大等待时间，可通过 {@link #setModuleEventTimeoutMs(long)} 调整。
     */
    private volatile long moduleEventTimeoutMs = MODULE_EVENT_TIMEOUT_MS;

    /**
     * 按 ModuleType 分组注册的模块事件兜底回调。
     * 同一模块类型可注册多个；兜底时优先遍历这些回调，全部执行完后不再走 PluginServerListener 的兜底。
     */
    private final Map<ModuleType, Set<Consumer<ModuleEventPayload>>> fallbackCallbacks =
            new ConcurrentHashMap<ModuleType, Set<Consumer<ModuleEventPayload>>>();

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
        this(39001);
    }

    public PluginServer(int port) {
        this("127.0.0.1", port);
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
     * 设置某个插件应用的前后台状态。模块事件分发时前台应用优先。
     */
    public void setAppState(String packageName, boolean foreground) {
        PluginSession session = getSession(packageName);
        if (session != null) {
            session.setForeground(foreground);
        }
    }

    /**
     * 将指定包名设为唯一前台应用，其余在线插件置为后台。
     */
    public void setForegroundApp(String packageName) {
        for (PluginSession session : sessions.all()) {
            session.setForeground(session.packageName().equals(packageName));
        }
    }

    /**
     * Find the plugin that registered {@code actionId} and invoke it.
     * If more than one plugin owns the same actionId, use
     * {@link #call(String, String, Map)} with packageName.
     */

    public CompletableFuture<CallResultPayload> call(String packageName, String actionId, Map<String, Object> params) {
        PluginSession session;
        try {
            session = getValidSessionOrThrow(packageName, actionId);
            if (!session.hasFeatures(actionId, Features.CALL)) {
                throw new IllegalStateException("package " + packageName + " has no actionId=" + actionId + ",cannot support server call");
            }
        } catch (IllegalStateException e) {
            return failedFuture(e);
        }

        CallPayload payload = new CallPayload();
        payload.actionId = actionId;
        payload.params = params;
        payload.idempotencyKey = RequestIds.next();
        return request(session, MessageType.CALL, payload, CallResultPayload.class);
    }


    public CompletableFuture<TopicResultPayload> publish(String packageName, Topic topic, Object data) {
        PluginSession session;
        try {
            session = getValidSession(packageName);
        } catch (IllegalStateException e) {
            return failedFuture(e);
        }
        TopicPayload payload = new TopicPayload();
        payload.topic = topic;
        payload.data = data;
        return request(session, MessageType.TOPIC, payload, TopicResultPayload.class);
    }

    /**
     * 透传一个模块事件：按“前台优先、后台其次”的顺序分发给已订阅该模块的插件。
     * 所有插件都未消费时调用 {@link PluginServerListener#onModuleEventFallback}。
     *
     * @param module   模块类型
     * @param moduleId 模块实例 id，用于区分同一模块类型下的不同设备/实例
     * @param action   模块内的事件动作，如 play / pause
     * @param event    模块事件原始消息
     */
    public CompletableFuture<ModuleEventDispatchResult> sendModuleEvent(
            ModuleType module, String moduleId, String action, Object event) {
        return sendModuleEvent(module, moduleId, action, event, moduleEventTimeoutMs);
    }

    /**
     * 同 {@link #sendModuleEvent(ModuleType, String, String, Object)}，
     * 但可指定等待单个插件回复的超时时间（毫秒）。
     */
    public CompletableFuture<ModuleEventDispatchResult> sendModuleEvent(
            ModuleType module, String moduleId, String action, Object event, long timeoutMs) {
        if (module == null) {
            return failedFuture(new IllegalArgumentException("module is required"));
        }
        if (moduleId == null || moduleId.isEmpty()) {
            return failedFuture(new IllegalArgumentException("moduleId is required"));
        }
        ModuleEventPayload payload = new ModuleEventPayload();
        payload.module = module;
        payload.moduleId = moduleId;
        payload.action = action;
        payload.timestamp = System.currentTimeMillis();
        payload.event = event;
        return dispatchModuleEvent(payload, timeoutMs);
    }

    /**
     * 设置等待单个插件回复 consume/ignore 的默认超时时间（毫秒）。
     * 插件超时未回复视为未消费，继续尝试下一个候选或进入兜底。
     */
    public void setModuleEventTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        this.moduleEventTimeoutMs = timeoutMs;
    }

    /**
     * 注册模块事件兜底回调。当所有已订阅该模块的插件都没有消费事件时触发。
     * 同一 {@link ModuleType} 可注册多个回调；兜底时会依次执行全部回调。
     * <p>
     * 注册了该 ModuleType 的回调后，将<b>不再</b>调用 {@link PluginServerListener#onModuleEventFallback}；
     * 只有未注册任何回调的 ModuleType 才会走 listener 的兜底。
     *
     * @param module   模块类型
     * @param callback 兜底回调，接收未被任何插件消费的 {@link ModuleEventPayload}
     */
    public void registerFallback(ModuleType module, Consumer<ModuleEventPayload> callback) {
        if (module == null || callback == null) {
            throw new IllegalArgumentException("module and callback must not be null");
        }
        Set<Consumer<ModuleEventPayload>> bucket = fallbackCallbacks.computeIfAbsent(
                module, k -> ConcurrentHashMap.newKeySet());
        bucket.add(callback);
    }

    /**
     * 解绑之前通过 {@link #registerFallback} 注册的兜底回调。
     *
     * @param module   模块类型
     * @param callback 要解绑的回调实例
     */
    public void unregisterFallback(ModuleType module, Consumer<ModuleEventPayload> callback) {
        if (module == null || callback == null) {
            return;
        }
        Set<Consumer<ModuleEventPayload>> bucket = fallbackCallbacks.get(module);
        if (bucket == null) {
            return;
        }
        bucket.remove(callback);
        if (bucket.isEmpty()) {
            fallbackCallbacks.remove(module);
        }
    }

    /**
     * 解绑指定模块类型下所有已注册的兜底回调。
     */
    public void removeAllFallbacks(ModuleType module) {
        if (module == null) {
            return;
        }
        fallbackCallbacks.remove(module);
    }

    /**
     * 透传一个已经构造好的模块事件。
     */
    public CompletableFuture<ModuleEventDispatchResult> dispatchModuleEvent(ModuleEventPayload event) {
        return dispatchModuleEvent(event, moduleEventTimeoutMs);
    }

    /**
     * 透传一个已经构造好的模块事件，并指定等待单个插件回复的超时时间（毫秒）。
     */
    public CompletableFuture<ModuleEventDispatchResult> dispatchModuleEvent(ModuleEventPayload event, long timeoutMs) {
        if (event == null || event.module == null) {
            return failedFuture(new IllegalArgumentException("module event requires module"));
        }
        if (event.timestamp == null) {
            event.timestamp = System.currentTimeMillis();
        }
        List<PluginSession> candidates = moduleEventCandidates(event.module);
        CompletableFuture<ModuleEventDispatchResult> result =
                new CompletableFuture<ModuleEventDispatchResult>();
        dispatchModuleEventNext(candidates, 0, event, timeoutMs, result);
        return result;
    }

    /**
     * 前台应用在前、后台应用在后；同类中优先最近一次前台/最近活跃的应用。
     */
    private List<PluginSession> moduleEventCandidates(ModuleType module) {
        List<PluginSession> candidates = new ArrayList<PluginSession>();
        for (PluginSession session : sessions.all()) {
            if (session.handlesModuleEvent(module)
                    && session.channel() != null
                    && session.channel().isActive()) {
                candidates.add(session);
            }
        }
        Collections.sort(candidates, new Comparator<PluginSession>() {
            @Override
            public int compare(PluginSession a, PluginSession b) {
                if (a.isForeground() != b.isForeground()) {
                    return a.isForeground() ? -1 : 1;
                }
                if (a.isForeground()) {
                    return Long.compare(b.foregroundAtMillis(), a.foregroundAtMillis());
                }
                return Long.compare(b.lastSeenMillis(), a.lastSeenMillis());
            }
        });
        return candidates;
    }

    private void dispatchModuleEventNext(final List<PluginSession> candidates,
                                         final int index,
                                         final ModuleEventPayload event,
                                         final long timeoutMs,
                                         final CompletableFuture<ModuleEventDispatchResult> result) {
        if (index >= candidates.size()) {
            ModuleEventPayload fallbackEvent = event.copyFor(ModuleEventPayload.APP_STATE_NONE);
            invokeModuleEventFallback(fallbackEvent);
            result.complete(ModuleEventDispatchResult.fallback(fallbackEvent));
            return;
        }
        final PluginSession session = candidates.get(index);
        String appState = session.isForeground()
                ? ModuleEventPayload.APP_STATE_FOREGROUND
                : ModuleEventPayload.APP_STATE_BACKGROUND;
        final ModuleEventPayload targeted = event.copyFor(appState);
        request(session, MessageType.MODULE_EVENT, targeted, ModuleEventResultPayload.class,
                timeoutMs).whenComplete(
                new java.util.function.BiConsumer<ModuleEventResultPayload, Throwable>() {
                    @Override
                    public void accept(ModuleEventResultPayload ack, Throwable error) {
                        if (error == null && ack != null && Boolean.TRUE.equals(ack.consumed)) {
                            result.complete(ModuleEventDispatchResult.handled(
                                    session.packageName(), targeted));
                        } else {
                            dispatchModuleEventNext(candidates, index + 1, event, timeoutMs, result);
                        }
                    }
                });
    }

    private void invokeModuleEventFallback(ModuleEventPayload event) {
        ModuleType module = event == null ? null : event.module;
        Set<Consumer<ModuleEventPayload>> bucket = module == null ? null : fallbackCallbacks.get(module);
        if (bucket != null && !bucket.isEmpty()) {
            Iterator<Consumer<ModuleEventPayload>> it = bucket.iterator();
            while (it.hasNext()) {
                Consumer<ModuleEventPayload> cb = it.next();
                try {
                    cb.accept(event);
                } catch (Exception e) {
                    log.error("registered fallback callback failed module={}", module, e);
                }
            }
        } else {
            try {
                listener.onModuleEventFallback(event);
            } catch (Exception e) {
                log.error("module event fallback failed module={}", module, e);
            }
        }
    }

    private PluginSession getValidSession(String packageName) {
        PluginSession session = getSession(packageName);
        if (session == null || session.channel() == null || !session.channel().isActive()) {
            throw new IllegalStateException("no active session for packageName=" + packageName);
        }
        return session;
    }

    // 提取出的公共获取 Session 方法
    private PluginSession getValidSessionOrThrow(String packageName, String actionId) {
        PluginSession session = getValidSession(packageName);
        if (!session.hasAction(actionId)) {
            throw new IllegalStateException("package " + packageName + " has no actionId=" + actionId);
        }
        return session;
    }


    public CompletableFuture<DescribeResultPayload> getDescribe(String packageName, String actionId) {
        PluginSession session;
        try {
            session = getValidSessionOrThrow(packageName, actionId);
        } catch (IllegalStateException e) {
            return failedFuture(e);
        }
        DescribePayload payload = new DescribePayload(actionId);
        payload.actionId = actionId;
        return request(session, MessageType.DESCRIBE, payload, DescribeResultPayload.class);
    }

    public CompletableFuture<GetOptionsResultPayload> getOptions(String packageName,
                                                                 String actionId,
                                                                 String describeId,
                                                                 String searchText,
                                                                 String cursor, int limit,
                                                                 Map<String, Object> params) {
        PluginSession session;
        try {
            session = getValidSessionOrThrow(packageName, actionId);
        } catch (IllegalStateException e) {
            return failedFuture(e);
        }
        GetOptionsPayload payload = new GetOptionsPayload();
        payload.params = params;
        payload.cursor = cursor;
        payload.limit = limit;
        payload.describeId = describeId;
        payload.searchText = searchText;
        payload.actionId = actionId;
        return request(session, MessageType.GET_OPTIONS, payload, GetOptionsResultPayload.class);
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
            case TOPIC_RESULT:
            case MODULE_EVENT_RESULT:
            case HEARTBEAT_ACK:
                pending.complete(envelope.getRequestId(), envelope.getPayload());
                return;
            case TASK_UPDATE:
                handleTaskUpdate(channel, envelope);
                return;
            case REPORT:
                handleReport(channel, envelope);
                return;
            case EVENT:
                handleEvent(channel, envelope);
                return;
            case MODULE_EVENT_REGISTER:
                handleModuleEventRegister(channel, envelope);
                return;
            case MODULE_EVENT_UNREGISTER:
                handleModuleEventUnregister(channel, envelope);
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
                payload.name.trim(),
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
        session.updateTopic(payload == null ? null : payload.topic);
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

    private void handleReport(Channel channel, Envelope envelope) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            log.warn("handleReport without session from {}", channel.remoteAddress());
            return;
        }
        session.touch();
        ReportPayload report = envelope.payloadAs(ReportPayload.class);
        ReportResultPayload ack = new ReportResultPayload();
        if (!session.hasFeatures(report.actionId, Features.REPORT_DATA)) {
            ack = new ReportResultPayload();
            ack.success = false;
            ack.error = ErrorInfo.of(ErrorCodes.NO_SUPPORT_FETURES, "actionId:" + report.actionId + " no support report", false, null);
        } else {
            listener.onReport(session, report);
            ack.success = true;
        }
        write(channel, Envelope.of(MessageType.REPORT_RESULT, envelope.getRequestId(), ack).withSession(session.sessionId(), session.sessionToken()));
    }

    private void handleEvent(Channel channel, Envelope envelope) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            log.warn("handleReport without session from {}", channel.remoteAddress());
            return;
        }
        session.touch();
        EventPayload event = envelope.payloadAs(EventPayload.class);
        EventResultPayload ack = new EventResultPayload();
        if (!session.hasFeatures(event.eventId, Features.EVENT)) {
            ack = new EventResultPayload();
            ack.success = false;
            ack.error = ErrorInfo.of(ErrorCodes.NO_SUPPORT_FETURES, "eventId:" + event.eventId + " no support event", false, null);
        } else {
            listener.onEventReceived(session, event);
            ack.success = true;
        }
        write(channel, Envelope.of(MessageType.EVENT_RESULT, envelope.getRequestId(), ack).withSession(session.sessionId(), session.sessionToken()));
    }

    private void handleModuleEventRegister(Channel channel, Envelope envelope) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            log.warn("module_event_register without session from {}", channel.remoteAddress());
            return;
        }
        ModuleEventRegisterPayload payload = envelope.payloadAs(ModuleEventRegisterPayload.class);
        if (payload == null || payload.modules == null || payload.modules.isEmpty()) {
            log.warn("module_event_register missing modules from {}", channel.remoteAddress());
            return;
        }
        session.registerModuleEvents(payload.modules);
    }

    private void handleModuleEventUnregister(Channel channel, Envelope envelope) {
        PluginSession session = sessions.resolve(channel, envelope.getSessionId());
        if (session == null) {
            log.warn("module_event_unregister without session from {}", channel.remoteAddress());
            return;
        }
        ModuleEventUnregisterPayload payload = envelope.payloadAs(ModuleEventUnregisterPayload.class);
        if (payload == null || payload.modules == null || payload.modules.isEmpty()) {
            log.warn("module_event_unregister missing modules from {}", channel.remoteAddress());
            return;
        }
        session.unregisterModuleEvents(payload.modules);
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
        return request(session, type, payload, responseType, CALL_TIMEOUT_MS);
    }

    private <T> CompletableFuture<T> request(
            PluginSession session, MessageType type, Payload payload, Class<T> responseType, long timeoutMs) {
        Channel channel = session.channel();
        String requestId = RequestIds.next();
        CompletableFuture<T> future = pending.register(requestId, responseType, timeoutMs);
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
