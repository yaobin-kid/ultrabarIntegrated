package com.example.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PluginInfo {
  public String id;
  public String name;
  public String version;
  public String packageName;
  public String signature;

  public PluginInfo() {}
}
