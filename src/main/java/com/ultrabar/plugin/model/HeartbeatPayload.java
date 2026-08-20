package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatPayload implements Payload {
    private String sessionId;
    private HeartbeatStatus status;

    public HeartbeatPayload() {}

    public HeartbeatPayload(String sessionId, HeartbeatStatus status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public HeartbeatStatus getStatus() {
        return status;
    }

    public void setStatus(HeartbeatStatus status) {
        this.status = status;
    }
}
