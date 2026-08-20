package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Line protocol envelope: {@code type} + generic {@code payload}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = EnvelopeDeserializer.class)
public class Envelope {
    private MessageType type;
    private String requestId;
    private String timestamp;
    private Protocol protocol;
    private String sessionId;
    private String auth;
    private Payload payload;

    public Envelope() {}

    public static Envelope of(MessageType type, String requestId, Payload payload) {
        Envelope envelope = new Envelope();
        envelope.setType(type);
        envelope.setRequestId(requestId);
        envelope.setTimestamp(Timestamps.utcNow());
        envelope.setProtocol(Protocol.current());
        envelope.setPayload(payload);
        return envelope;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public Envelope withSession(String sessionId, String auth) {
        this.sessionId = sessionId;
        this.auth = auth;
        return this;
    }

    @JsonIgnore
    public <T extends Payload> T payloadAs(Class<T> payloadClass) {
        if (payload == null) {
            return null;
        }
        if (!payloadClass.isInstance(payload)) {
            throw new IllegalStateException(
                    "payload type mismatch: expected " + payloadClass.getSimpleName()
                            + " but was " + payload.getClass().getSimpleName());
        }
        return payloadClass.cast(payload);
    }
}
