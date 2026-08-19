package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskInfo {
  public String taskId;
  public String status; // pending|running|succeeded|failed|cancelled
  public String statusUrl;

  public TaskInfo() {}
}
