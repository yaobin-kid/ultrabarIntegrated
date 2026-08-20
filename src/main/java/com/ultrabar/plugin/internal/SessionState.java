package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.RegisterResultPayload;

public final class SessionState {
    private static final long DEFAULT_HEARTBEAT_INTERVAL_MS = 30_000L;

    private volatile String sessionId;
    private volatile String sessionToken;
    private volatile long heartbeatIntervalMillis = DEFAULT_HEARTBEAT_INTERVAL_MS;

    public SessionState() {}

    void apply(RegisterResultPayload result) {
        this.sessionId = result.sessionId;
        this.sessionToken = result.sessionToken;
        if (result.heartbeat != null
                && result.heartbeat.interval != null
                && result.heartbeat.interval > 0) {
            this.heartbeatIntervalMillis = result.heartbeat.interval.longValue();
        }
    }

    void clear() {
        sessionId = null;
        sessionToken = null;
    }

    boolean isOpen() {
        return sessionId != null;
    }

    String sessionId() {
        return sessionId;
    }

    long heartbeatIntervalMillis() {
        return heartbeatIntervalMillis;
    }
}
