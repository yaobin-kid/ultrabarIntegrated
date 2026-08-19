package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescribeResultPayload {
  public Boolean success;
  public Map<String, Object> details;
  public ErrorInfo error;

  public DescribeResultPayload() {}

  public DescribeResultPayload(Boolean success, Map<String, Object> details, ErrorInfo error) {
    this.success = success;
    this.details = details;
    this.error = error;
  }
}
