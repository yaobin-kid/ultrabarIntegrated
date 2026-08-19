package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterPayload {
  public PluginInfo plugin;
  public ConfigServer configServer;
  public RegisterPayload() {}
}
