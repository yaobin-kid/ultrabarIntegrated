package com.example.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigServer {
  public String host;
  public Integer port;
  public String url;
  public ConfigServer() {}
}
