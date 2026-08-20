package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetOptionsPayload implements Payload {
    public String actionId;
    public String parameterId;
    public String searchText;
    public Map<String, Object> params;
    public String cursor;
    public Integer limit;

    public GetOptionsPayload() {}
}
