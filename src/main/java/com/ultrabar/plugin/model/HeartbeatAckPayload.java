package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeartbeatAckPayload extends ReplyPayload {
    public HeartbeatAckPayload() {}
}
