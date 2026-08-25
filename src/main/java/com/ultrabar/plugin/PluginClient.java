package com.ultrabar.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.internal.*;
import com.ultrabar.plugin.model.*;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * Push a later status for an accepted call. {@code requestId} is new; correlate with {@code task.taskId}.
     */
    public void sendTaskUpdate(TaskUpdatePayload payload) {
        envelopes.sendOneWay(MessageType.TASK_UPDATE, payload);
    }

    /**
     * 上报属性
     *
     * @param actionId
     * @param params
     */
    public void reportData(String actionId, Map<String, Object> params) {
        ReportPayload report = new ReportPayload();
        report.actionId = actionId;
        report.params = params;
        envelopes.sendOneWay(MessageType.REPORT, report);
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
