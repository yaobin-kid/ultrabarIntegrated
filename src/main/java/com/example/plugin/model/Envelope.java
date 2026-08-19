package com.example.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Envelope {
  public String type;
  public String requestId;
  public String timestamp;
  public Protocol protocol;
  public String sessionId;
  public String auth;
  public Object payload;

  public Envelope() {}
}
