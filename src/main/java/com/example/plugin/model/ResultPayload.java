package com.example.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultPayload {
  public Boolean success;
  public Map<String, Object> data;
  public Boolean accepted;
  public TaskInfo task;
  public ErrorInfo error;
  public ResultPayload() {}
}
