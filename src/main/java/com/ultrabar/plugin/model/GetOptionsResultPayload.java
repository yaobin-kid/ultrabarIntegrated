package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class GetOptionsResultPayload extends ReplyPayload {
    public Map<String, Object> details;
    public List<Label> items;
    public Boolean hasMore;
    public String nextCursor;

    public GetOptionsResultPayload() {}
}
