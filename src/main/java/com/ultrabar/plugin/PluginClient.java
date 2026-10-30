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
import com.ultrabar.plugin.model.AGenUIDataPayload;
import com.ultrabar.plugin.model.AGenUIDataResultPayload;
import com.ultrabar.plugin.model.AGenUIUpdateDataModelPayload;
import com.ultrabar.plugin.model.AGenUIUpdateDataModelResultPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.EventResultPayload;
import com.ultrabar.plugin.model.Json;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.ModuleEventRegisterPayload;
import com.ultrabar.plugin.model.ModuleEventUnregisterPayload;
import com.ultrabar.plugin.model.ModuleType;
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
import java.util.concurrent.ThreadFactory;
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
    private volatile boolean stopped;

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
     * 上报 agenui 数据到服务端。服务端会将数据缓存到 PluginSession 中，
     * 并通过 {@link PluginServerListener#onAGenUIData} 回调通知宿主。
     *
     * @param data 数据 JSON 字符串
     */
    public CompletableFuture<AGenUIDataResultPayload> sendAGenUIData(String data) {
        AGenUIDataPayload payload = new AGenUIDataPayload(data);
        return envelopes.request(MessageType.AGENUI_DATA, payload, AGenUIDataResultPayload.class);
    }

    /**
     * 上报 agenui updateDataModel 到服务端。服务端会将 dataModel 缓存到 PluginSession 中，
     * 并通过 {@link PluginServerListener#onAGenUIUpdateDataModel} 回调通知宿主。
     *
     * @param dataModel dataModel JSON 字符串
     */
    public CompletableFuture<AGenUIUpdateDataModelResultPayload> sendAGenUIUpdateDataModel(
            String dataModel) {
        return sendAGenUIUpdateDataModel(dataModel, false);
    }

    /**
     * 同 {@link #sendAGenUIUpdateDataModel(String)}，但可标记是否为增量更新。
     */
    public CompletableFuture<AGenUIUpdateDataModelResultPayload> sendAGenUIUpdateDataModel(
            String dataModel, boolean incremental) {
        AGenUIUpdateDataModelPayload payload = new AGenUIUpdateDataModelPayload(dataModel);
        payload.incremental = incremental;
        return envelopes.request(MessageType.AGENUI_UPDATE_DATA_MODEL, payload,
                AGenUIUpdateDataModelResultPayload.class);
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
