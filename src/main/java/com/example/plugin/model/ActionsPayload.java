package com.example.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionsPayload {
  public List<ActionSummary> actions;
  public ActionsPayload() {}
  public ActionsPayload(List<ActionSummary> actions) { this.actions = actions; }
}
