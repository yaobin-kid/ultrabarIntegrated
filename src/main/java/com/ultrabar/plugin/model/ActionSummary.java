package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionSummary {
    public String actionId;
    public String name;
    public String description;
    public final ActionType actionType;

    public ActionSummary(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionSummary() {
        this(ActionType.CALL);
    }
}
