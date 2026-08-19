package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionsAckPayload {
  public Boolean success;
  public Integer receivedCount;
  public ActionsAckPayload() {}
}
