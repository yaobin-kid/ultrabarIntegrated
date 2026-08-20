package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskUpdatePayload extends ReplyPayload {
    public TaskInfo task;
    public Map<String, Object> data;

    public TaskUpdatePayload() {}
}
