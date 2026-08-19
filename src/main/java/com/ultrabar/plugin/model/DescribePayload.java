package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescribePayload {
  public String actionId;

  public DescribePayload() {}

  public DescribePayload(String actionId) {
    this.actionId = actionId;
  }
}
