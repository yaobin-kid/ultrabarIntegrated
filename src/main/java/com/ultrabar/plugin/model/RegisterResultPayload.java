package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterResultPayload extends ReplyPayload {
    public String sessionId;
    public String sessionToken;
    public ConfigServer configServer;
    public Heartbeat heartbeat;

    public RegisterResultPayload() {}
}
