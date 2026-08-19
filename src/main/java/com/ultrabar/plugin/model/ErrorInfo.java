package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorInfo {
  public String code;
  public String message;
  public Boolean retryable;
  public Map<String, Object> details;
  public String timestamp;

  public ErrorInfo() {}
}
