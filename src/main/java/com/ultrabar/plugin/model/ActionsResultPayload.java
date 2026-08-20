package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionsResultPayload extends ReplyPayload {
    public Integer receivedCount;
    public Long revision;

    public ActionsResultPayload() {}
}
