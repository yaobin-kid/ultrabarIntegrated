package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class ReplyPayload implements Payload {
    public Boolean success;
    public ErrorInfo error;

    @JsonIgnore
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }
}
