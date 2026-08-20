package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Shared success/error shape for all reply payloads.
 * Do not add {@code getSuccess()/isSuccess()} — Jackson treats those as the {@code success}
 * property and will fail to bind the public field (success stays null, handshake thinks it failed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ReplyPayload implements Payload {
    public Boolean success;
    public ErrorInfo error;

    @JsonIgnore
    public boolean succeeded() {
        return Boolean.TRUE.equals(success);
    }
}
