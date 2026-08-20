package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.HeartbeatPayload;
import com.ultrabar.plugin.model.HeartbeatStatus;
import com.ultrabar.plugin.model.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class HeartbeatScheduler {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final ScheduledExecutorService scheduler;
    private final EnvelopeClient envelopes;
    private final SessionState session;
    private final LineConnection connection;

    private ScheduledFuture<?> task;

    public HeartbeatScheduler(
            ScheduledExecutorService scheduler,
            EnvelopeClient envelopes,
            SessionState session,
            LineConnection connection) {
        this.scheduler = scheduler;
        this.envelopes = envelopes;
        this.session = session;
        this.connection = connection;
    }

    synchronized void start() {
        stop();
        long interval = session.heartbeatIntervalMillis();
        if (interval <= 0) {
            return;
        }
        task = scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                beat();
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void beat() {
        if (!connection.isActive() || !session.isOpen()) {
            return;
        }
        try {
            envelopes.sendOneWay(MessageType.HEARTBEAT, new HeartbeatPayload(session.sessionId(), HeartbeatStatus.ALIVE));
        } catch (Exception e) {
            log.warn("failed to send heartbeat", e);
        }
    }
}
