package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskInfo {
    public String taskId;
    public TaskStatus status;
    public String statusUrl;

    public TaskInfo() {}

    public static TaskInfo pending(String taskId) {
        TaskInfo task = new TaskInfo();
        task.taskId = taskId;
        task.status = TaskStatus.PENDING;
        return task;
    }
}
