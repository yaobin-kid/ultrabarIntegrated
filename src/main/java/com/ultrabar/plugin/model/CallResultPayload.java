package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallResultPayload extends ReplyPayload {
    public Map<String, Object> data;
    public Boolean accepted;
    public TaskInfo task;

    public CallResultPayload() {}
}
