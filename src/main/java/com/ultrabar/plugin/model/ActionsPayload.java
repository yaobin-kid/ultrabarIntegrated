package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionsPayload implements Payload {
    public Long revision;
    public List<ActionSummary> actions;
    public Set<Topic> topic;


    public ActionsPayload() {
    }

    public ActionsPayload(List<ActionSummary> actions) {
        this.actions = actions;
    }
}
