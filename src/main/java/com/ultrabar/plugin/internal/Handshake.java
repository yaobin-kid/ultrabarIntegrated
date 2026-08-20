package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.ActionsResultPayload;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * On TCP connect: register, then actions, then heartbeat.
 */
public final class Handshake {
    private static final Logger log = LoggerFactory.getLogger(Handshake.class);
    private static final long RETRY_INTERVAL_MS = 3_000L;

    private final EnvelopeClient envelopes;
    private final SessionState session;
    private final HeartbeatScheduler heartbeat;
    private final ListenerNotifier notifier;
    private final LineConnection connection;
    private final ScheduledExecutorService scheduler;

    private volatile RegisterPayload registerConfig;
    private volatile ActionsPayload actionsConfig;
    private volatile boolean stopped;
    private ScheduledFuture<?> retry;

    public Handshake(
            EnvelopeClient envelopes,
            SessionState session,
            HeartbeatScheduler heartbeat,
            ListenerNotifier notifier,
            LineConnection connection,
            ScheduledExecutorService scheduler) {
        this.envelopes = envelopes;
        this.session = session;
        this.heartbeat = heartbeat;
        this.notifier = notifier;
        this.connection = connection;
        this.scheduler = scheduler;
    }

    public void setRegisterConfig(RegisterPayload payload) {
        this.registerConfig = payload;
    }

    public void setActionsConfig(ActionsPayload payload) {
        this.actionsConfig = payload;
    }

    public void stop() {
        stopped = true;
        cancelRetry();
    }

    public void onConnected() {
        if (stopped) {
            return;
        }
        cancelRetry();
        if (registerConfig != null) {
            doRegister();
        }
    }

    public void onDisconnected() {
        cancelRetry();
        heartbeat.stop();
        session.clear();
    }

    public void updateActions(ActionsPayload payload) {
        this.actionsConfig = payload;
        if (session.isOpen() && connection.isActive()) {
            sendActions();
        }
    }

    private void doRegister() {
        if (stopped || !connection.isActive()) {
            return;
        }
        RegisterPayload payload = registerConfig;
        if (payload == null) {
            return;
        }
        envelopes.request(MessageType.REGISTER, payload, RegisterResultPayload.class).whenComplete(new java.util.function.BiConsumer<RegisterResultPayload, Throwable>() {
            @Override
            public void accept(RegisterResultPayload result, Throwable error) {
                onRegisterDone(result, error);
            }
        });
    }

    private void onRegisterDone(RegisterResultPayload result, Throwable error) {
        if (stopped || !connection.isActive()) {
            return;
        }
        if (error != null) {
            log.warn("register failed: {}", error.toString());
            notifier.onRegisterFailed(error);
            scheduleRetry();
            return;
        }
        if (result == null || !result.succeeded()) {
            RuntimeException failure = new RuntimeException(
                    "register_result invalid: payload=" + result
                            + " success=" + (result == null ? "null" : result.success));
            notifier.onRegisterFailed(failure);
            scheduleRetry();
            return;
        }
        session.apply(result);
        heartbeat.start();
        notifier.onRegisterSuccess(result);
        if (actionsConfig != null) {
            sendActions();
        }
    }

    private void sendActions() {
        if (stopped || !connection.isActive() || !session.isOpen()) {
            return;
        }
        ActionsPayload payload = actionsConfig;
        if (payload == null) {
            return;
        }
        envelopes.request(MessageType.ACTIONS, payload, ActionsResultPayload.class).whenComplete(new java.util.function.BiConsumer<ActionsResultPayload, Throwable>() {
            @Override
            public void accept(ActionsResultPayload ack, Throwable error) {
                onActionsDone(ack, error);
            }
        });
    }

    private void onActionsDone(ActionsResultPayload ack, Throwable error) {
        if (stopped) {
            return;
        }
        if (error != null) {
            notifier.onActionsFailed(error);
            return;
        }
        if (ack == null || !ack.succeeded()) {
            notifier.onActionsFailed(new RuntimeException(
                    "actions_result invalid: payload=" + ack
                            + " success=" + (ack == null ? "null" : ack.success)));
            return;
        }
        notifier.onActionsAck(ack);
    }

    private void scheduleRetry() {
        cancelRetry();
        if (stopped) {
            return;
        }
        retry = scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                doRegister();
            }
        }, RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void cancelRetry() {
        ScheduledFuture<?> current = retry;
        retry = null;
        if (current != null) {
            current.cancel(false);
        }
    }
}
