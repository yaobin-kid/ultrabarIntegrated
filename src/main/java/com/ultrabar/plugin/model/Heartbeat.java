package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Heartbeat {
  public Integer interval;
  public Integer timeout;
  public Heartbeat() {}
}
