package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterResultPayload {
  public Boolean success;
  public String sessionId;
  public String sessionToken;
  public ConfigServer configServer;
  public Heartbeat heartbeat;
  public Protocol protocol;
  public RegisterResultPayload() {}
}
