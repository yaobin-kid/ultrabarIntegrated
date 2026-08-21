package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetOptionsPayload implements Payload {
    public String actionId;
    public String describeId;
    public String searchText;
    public Map<String, Object> params;
    public Integer cursor;
    public Integer limit = 100;

    public GetOptionsPayload() {
    }
}
