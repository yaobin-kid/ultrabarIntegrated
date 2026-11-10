package com.ultrabar.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.internal.EnvelopeClient;
import com.ultrabar.plugin.internal.Handshake;
import com.ultrabar.plugin.internal.HeartbeatScheduler;
import com.ultrabar.plugin.internal.InboundDispatcher;
import com.ultrabar.plugin.internal.LineConnection;
import com.ultrabar.plugin.internal.ListenerNotifier;
import com.ultrabar.plugin.internal.RequestTable;
import com.ultrabar.plugin.internal.SessionState;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.A2UIDataPayload;
import com.ultrabar.plugin.model.A2UIDataResultPayload;
import com.ultrabar.plugin.model.A2UIItem;
import com.ultrabar.plugin.model.A2UIUpdateDataModelPayload;
import com.ultrabar.plugin.model.A2UIUpdateDataModelResultPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.EventResultPayload;
import com.ultrabar.plugin.model.HotkeyPayload;
import com.ultrabar.plugin.model.HotkeyResultPayload;
import com.ultrabar.plugin.model.Json;
import com.ultrabar.plugin.model.MessageDestination;
import com.ultrabar.plugin.model.MessagePayload;
import com.ultrabar.plugin.model.MessageResultPayload;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.HostActionRequest;
import com.ultrabar.plugin.model.HostActionResultPayload;
import com.ultrabar.plugin.model.ModuleEventRegisterPayload;
import com.ultrabar.plugin.model.ModuleEventUnregisterPayload;
import com.ultrabar.plugin.model.ModuleOnlineFilter;
import com.ultrabar.plugin.model.ModuleType;
import com.ultrabar.plugin.model.QueryModulesPayload;
import com.ultrabar.plugin.model.QueryModulesResultPayload;
import com.ultrabar.plugin.model.RegisterPayload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.Channel;

/**
 * Plugin-side client for the Ultrabar line-delimited JSON protocol.
 * <p>
 * Typical usage: set register/actions config and a {@link PluginListener}, then {@link #startAsync()}.
 */
public class PluginClient {
    private static final Logger log = LoggerFactory.getLogger(PluginClient.class);

    private final LineConnection connection;
    private final RequestTable requests;
    private final EnvelopeClient envelopes;
    private final Handshake handshake;
    private final HeartbeatScheduler heartbeat;
    private final ListenerNotifier notifier;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService callbackExecutor;
    private final Set<ModuleType> moduleEvents = Collections.newSetFromMap(
            new ConcurrentHashMap<ModuleType, Boolean>());
    /** 已 down 尚未 up 的按键码 → 自动补发 up 的定时任务 */
    private final Map<Integer, ScheduledFuture<?>> pendingHotkeyUps =
            new ConcurrentHashMap<Integer, ScheduledFuture<?>>();
    /** hotkeyDown 后未配对 up 时，自动补发 up 的延迟（毫秒） */
    private volatile long hotkeyAutoReleaseMs = DEFAULT_HOTKEY_AUTO_RELEASE_MS;
    private volatile boolean stopped;

    private static final long DEFAULT_HOTKEY_AUTO_RELEASE_MS = 3000L;

    public PluginClient() {
        this("127.0.0.1", 39001);
    }

    public PluginClient(String host, int port) {
        ObjectMapper mapper = Json.mapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(namedFactory("plugin-client-scheduler"));
        this.callbackExecutor = Executors.newCachedThreadPool(namedFactory("plugin-client-callback"));
        this.notifier = new ListenerNotifier(callbackExecutor);

        SessionState session = new SessionState();
        this.requests = new RequestTable(scheduler);
        InboundDispatcher dispatcher = new InboundDispatcher(mapper, requests, notifier, session);
        this.connection = new LineConnection(host, port, mapper, new LineConnection.Listener() {
            @Override
            public void onConnected(Channel channel) {
                handshake.onConnected();
            }

            @Override
            public void onDisconnected() {
                requests.failAll(new IOException("disconnected"));
                handshake.onDisconnected();
            }

            @Override
            public void onMessage(Channel channel, String line) {
                dispatcher.onMessage(channel, line);
            }
        });

        EnvelopeClient envelopes = new EnvelopeClient(connection, requests, session);
        this.envelopes = envelopes;
        this.heartbeat = new HeartbeatScheduler(scheduler, envelopes, session, connection);
        this.handshake = new Handshake(envelopes, session, heartbeat, notifier, connection, scheduler);
        handshake.setOnSessionReady(new Runnable() {
            @Override
            public void run() {
                resendModuleEvents();
            }
        });
    }

    public void setRegisterConfig(RegisterPayload payload) {
        handshake.setRegisterConfig(payload);
    }

    public void setActionsConfig(ActionsPayload payload) {
        handshake.setActionsConfig(payload);
    }

    public void updateActions(ActionsPayload payload) {
        handshake.updateActions(payload);
    }

    public void setPluginListener(PluginListener listener) {
        notifier.setListener(listener);
    }

    public CompletableFuture<EventResultPayload> sendEvent(String eventId, Map<String, Object> params) {
        EventPayload event = new EventPayload();
        event.eventId = eventId;
        event.params = params;
        event.timestamp = System.currentTimeMillis();
        return envelopes.request(MessageType.EVENT, event, EventResultPayload.class);
    }

    /**
     * 订阅模块事件。可一次传入多个模块，订阅后服务端才会把对应模块的事件分发给本插件。
     * <p>
     * 可在 {@link #startAsync()} 前调用；每次注册/重连成功后会自动重新订阅。
     */
    public void subscribeModuleEvents(ModuleType... modules) {
        if (modules == null || modules.length == 0) {
            return;
        }
        List<ModuleType> added = new ArrayList<ModuleType>();
        for (ModuleType module : modules) {
            if (module != null && moduleEvents.add(module)) {
                added.add(module);
            }
        }
        //会话尚未建立时先记录，注册成功后由 resendModuleEvents() 统一发送。
        if (!added.isEmpty() && handshake.isSessionReady()) {
            envelopes.sendOneWay(MessageType.MODULE_EVENT_REGISTER,
                    new ModuleEventRegisterPayload(added));
        }
    }

    /**
     * 取消模块事件订阅。可一次传入多个模块。
     */
    public void unsubscribeModuleEvents(ModuleType... modules) {
        if (modules == null || modules.length == 0) {
            return;
        }
        List<ModuleType> removed = new ArrayList<ModuleType>();
        for (ModuleType module : modules) {
            if (module != null && moduleEvents.remove(module)) {
                removed.add(module);
            }
        }
        if (!removed.isEmpty() && handshake.isSessionReady()) {
            envelopes.sendOneWay(MessageType.MODULE_EVENT_UNREGISTER,
                    new ModuleEventUnregisterPayload(removed));
        }
    }

    private void resendModuleEvents() {
        if (moduleEvents.isEmpty()) {
            return;
        }
        envelopes.sendOneWay(MessageType.MODULE_EVENT_REGISTER,
                new ModuleEventRegisterPayload(new ArrayList<ModuleType>(moduleEvents)));
    }

    /**
     * 主动上报（刷新）单个 a2ui 布局到服务端。
     * 服务端会将数据缓存到 PluginSession 中，
     * 并通过 {@link PluginServerListener#onA2UIData} 回调通知宿主。
     *
     * <p>若需要服务端主动拉取当前全部 a2ui 数据，服务端可调用
     * {@code PluginServer.queryA2UIData(packageName)}，此时插件通过
     * {@link PluginListener#onA2UIDataRequest()} 返回数据。</p>
     *
     * @param item a2ui 布局项（layoutId + ui JSON）
     */
    public CompletableFuture<A2UIDataResultPayload> sendA2UIData(A2UIItem item) {
        List<A2UIItem> items = item == null
                ? Collections.<A2UIItem>emptyList()
                : Collections.singletonList(item);
        A2UIDataPayload payload = new A2UIDataPayload(items);
        return envelopes.request(MessageType.A2UI_DATA, payload, A2UIDataResultPayload.class);
    }

    /**
     * 同 {@link #sendA2UIData(A2UIItem)}，批量上报多个布局。
     */
    public CompletableFuture<A2UIDataResultPayload> sendA2UIData(List<A2UIItem> items) {
        A2UIDataPayload payload = new A2UIDataPayload(items);
        return envelopes.request(MessageType.A2UI_DATA, payload, A2UIDataResultPayload.class);
    }

    /**
     * 上报 a2ui updateDataModel 到服务端。服务端会将 dataModel 缓存到 PluginSession 中，
     * 并通过 {@link PluginServerListener#onA2UIUpdateDataModel} 回调通知宿主。
     *
     * @param layoutId  布局 id
     * @param dataModel dataModel JSON 字符串
     */
    public CompletableFuture<A2UIUpdateDataModelResultPayload> sendA2UIUpdateDataModel(
            String layoutId, String dataModel) {
        return sendA2UIUpdateDataModel(layoutId, dataModel, false);
    }

    /**
     * 同 {@link #sendA2UIUpdateDataModel(String, String)}，但可标记是否为增量更新。
     */
    public CompletableFuture<A2UIUpdateDataModelResultPayload> sendA2UIUpdateDataModel(
            String layoutId, String dataModel, boolean incremental) {
        A2UIUpdateDataModelPayload payload = new A2UIUpdateDataModelPayload(layoutId, dataModel);
        payload.incremental = incremental;
        return envelopes.request(MessageType.A2UI_UPDATE_DATA_MODEL, payload,
                A2UIUpdateDataModelResultPayload.class);
    }

    /**
     * 短按热键：SDK 自动补齐 down + up，无需配对调用。支持单键与组合键。
     * <p>
     * 每个按键在服务端至少产生 2 次回调（down + up）：
     * down 按参数顺序发送，up 按<b>逆序</b>发送（修饰键先按后抬）。
     * 消息 {@code pressType = short}。
     * <p>
     * 示例（Windows VK 码）：复制 {@code sendHotkey(0x11, 0x43)}（Ctrl+C）；
     * PS 放大画笔一次 {@code sendHotkey(0x11, 0xDD)}（Ctrl+]）。
     *
     * @param keys 原始按键码，组合键按按下顺序排列（修饰键在前）
     * @return 最后一个 up 消息的回执（channel 写有序，前面的 down/up 均已送达）
     */
    public CompletableFuture<HotkeyResultPayload> sendHotkey(int... keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        for (int key : keys) {
            doSendHotkey(HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_SHORT, key);
        }
        CompletableFuture<HotkeyResultPayload> result = null;
        for (int i = keys.length - 1; i >= 0; i--) {
            result = doSendHotkey(HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_SHORT, keys[i]);
        }
        return result;
    }

    /**
     * 长按按下（不抬起），必须与 {@link #hotkeyUp(int...)} 配对。
     * 消息 {@code pressType = long}。
     * <p>
     * 需要"按住持续生效"的效果（如 PS 中 Ctrl+] 持续线性放大画笔）时，
     * 按住期间以固定间隔（如 80ms）重复调用本方法发送同一个键的 down，
     * 模拟键盘硬件自动重复；结束时调用 {@code hotkeyUp} 收尾。
     * <p>
     * 若在 {@link #setHotkeyAutoReleaseMs(long)} 设定的时长内（默认 3000ms，
     * 设为 0 可关闭）仍未调用 {@code hotkeyUp}，SDK 会自动补发一次 up，防止按键卡死。
     *
     * @param keys 原始按键码；多键时按参数顺序逐个 down
     * @return 最后一个 down 消息的回执
     */
    public CompletableFuture<HotkeyResultPayload> hotkeyDown(int... keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        CompletableFuture<HotkeyResultPayload> result = null;
        for (int key : keys) {
            result = doSendHotkey(HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, key);
            scheduleHotkeyAutoRelease(key);
        }
        return result;
    }

    /**
     * 长按抬起，与 {@link #hotkeyDown(int...)} 配对。消息 {@code pressType = long}。
     * 多键时按<b>逆序</b>逐个 up（与短按一致的释放语义），并取消各键的自动补发任务。
     *
     * @param keys 原始按键码，传参与对应 {@code hotkeyDown} 相同即可（内部自动逆序）
     * @return 最后一个 up 消息的回执
     */
    public CompletableFuture<HotkeyResultPayload> hotkeyUp(int... keys) {
        if (keys == null || keys.length == 0) {
            throw new IllegalArgumentException("keys must not be empty");
        }
        CompletableFuture<HotkeyResultPayload> result = null;
        for (int i = keys.length - 1; i >= 0; i--) {
            cancelHotkeyAutoRelease(keys[i]);
            result = doSendHotkey(HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, keys[i]);
        }
        return result;
    }

    /**
     * 设置 {@code hotkeyDown} 后自动补发 up 的延迟毫秒数（默认 3000ms）。
     * 设为 0 或负数则关闭自动补发（用于需要长时间按住的场景）。
     */
    public void setHotkeyAutoReleaseMs(long autoReleaseMs) {
        this.hotkeyAutoReleaseMs = autoReleaseMs;
    }

    private CompletableFuture<HotkeyResultPayload> doSendHotkey(String action, String pressType, int key) {
        HotkeyPayload payload = new HotkeyPayload(action, pressType,
                Collections.singletonList(key));
        return envelopes.request(MessageType.HOTKEY, payload, HotkeyResultPayload.class);
    }

    /** 为 down 的按键登记自动补发 up；重复 down 同一键会重置计时。 */
    private void scheduleHotkeyAutoRelease(final Integer key) {
        if (key == null || hotkeyAutoReleaseMs <= 0) {
            return;
        }
        cancelHotkeyAutoRelease(key);
        ScheduledFuture<?> future = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (pendingHotkeyUps.remove(key) != null && !stopped) {
                    log.warn("hotkey 0x{} down without up for {}ms, auto releasing",
                            Integer.toHexString(key).toUpperCase(), hotkeyAutoReleaseMs);
                    doSendHotkey(HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, key);
                }
            }
        }, hotkeyAutoReleaseMs, TimeUnit.MILLISECONDS);
        pendingHotkeyUps.put(key, future);
    }

    private void cancelHotkeyAutoRelease(Integer key) {
        ScheduledFuture<?> future = pendingHotkeyUps.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 向指定实例发送文本消息（仅支持文本），默认透传到 Ultra WorkSpace PLUGIN 端。
     * 服务端收到后回调 {@code PluginServerListener#onMessage}，
     * 返回值中的 success=false 表示处理失败。
     *
     * @param instanceId 目标实例 id（由接收端定义与解释）
     * @param message    文本消息内容，不能为空
     */
    public CompletableFuture<MessageResultPayload> sendMessage(String instanceId,
                                                               String message) {
        return sendMessage(MessageDestination.ULTRA_WORKSPACE_PLUGIN, instanceId, message);
    }

    /**
     * 向指定实例发送文本消息（仅支持文本），可指定透传目的地。
     * 其他透传场景换 destination 参数即可。
     *
     * @param destination 消息透传目的地，见 {@link MessageDestination}
     * @param instanceId  目标实例 id（由接收端定义与解释）
     * @param message     文本消息内容，不能为空
     */
    public CompletableFuture<MessageResultPayload> sendMessage(MessageDestination destination,
                                                               String instanceId,
                                                               String message) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        if (instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("instanceId must not be empty");
        }
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must not be empty");
        }
        return envelopes.request(MessageType.MESSAGE,
                new MessagePayload(destination, instanceId, message),
                MessageResultPayload.class);
    }

    /**
     * 请求宿主（PC）端执行指定动作（打开文件、启动应用、打开浏览器、输入文本、执行命令等）。
     * 请求对象通过 {@link HostActionRequest} 的 Builder 链式构造，参数键由 SDK 内聚：
     *
     * <pre>
     * sendHostAction(HostActionRequest.open().path("C:/Users/kid/Desktop/ap").build());
     * sendHostAction(HostActionRequest.startApp().path("C:/.../SourceTree.exe").launchMode(2).build());
     * sendHostAction(HostActionRequest.openBrowser().url("google.com").build());
     * sendHostAction(HostActionRequest.inputText().text("hello").build());
     * sendHostAction(HostActionRequest.execCmd().cmd("notepad").build());
     * </pre>
     *
     * 服务端收到后回调 {@code PluginServerListener#onHostAction}，
     * 通过 {@code request.action} 区分动作类型、{@code request.params} 取参数；
     * 返回值中的 success=false 表示执行失败。
     *
     * @param request 动作请求，见 {@link HostActionRequest} 的 Builder
     */
    public CompletableFuture<HostActionResultPayload> sendHostAction(HostActionRequest request) {
        if (request == null || request.action == null) {
            throw new IllegalArgumentException("request.action must not be null");
        }
        return envelopes.request(MessageType.HOST_ACTION, request, HostActionResultPayload.class);
    }

    /**
     * 查询模块列表。服务端收到后会回调 {@link PluginServerListener#onQueryModules}
     * 由宿主提供模块数据，按 filter 过滤后封装返回。
     *
     * @param filter 在线状态过滤（ONLINE 在线 / OFFLINE 离线 / ALL 全部），null 视为 ALL
     */
    public CompletableFuture<QueryModulesResultPayload> queryModules(ModuleOnlineFilter filter) {
        QueryModulesPayload payload = new QueryModulesPayload(
                filter == null ? ModuleOnlineFilter.ALL : filter);
        return envelopes.request(MessageType.QUERY_MODULES, payload, QueryModulesResultPayload.class);
    }

    /**
     * 查询全部模块（在线 + 离线）。
     */
    public CompletableFuture<QueryModulesResultPayload> queryModules() {
        return queryModules(ModuleOnlineFilter.ALL);
    }

    public CompletableFuture<Void> startAsync() {
        if (stopped) {
            CompletableFuture<Void> failed = new CompletableFuture<Void>();
            failed.completeExceptionally(new IllegalStateException("client already stopped"));
            return failed;
        }
        return connection.start();
    }

    public void stop() {
        stopped = true;
        for (ScheduledFuture<?> future : pendingHotkeyUps.values()) {
            future.cancel(false);
        }
        pendingHotkeyUps.clear();
        handshake.stop();
        heartbeat.stop();
        connection.stop();
        requests.failAll(new IllegalStateException("client stopped"));
        scheduler.shutdownNow();
        callbackExecutor.shutdownNow();
        log.info("plugin client stopped");
    }

    private static ThreadFactory namedFactory(final String prefix) {
        final AtomicInteger index = new AtomicInteger(1);
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, prefix + "-" + index.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

}
