package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallPayload {
  public String action;
  public Map<String, Object> params;
  public String idempotencyKey;
  public CallPayload() {}
}
