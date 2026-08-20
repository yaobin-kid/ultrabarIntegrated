package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionSummary {
    public String actionId;
    public Integer version;
    public String name;
    public String description;

    public ActionSummary() {}
}
