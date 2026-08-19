package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Protocol {
  public String name;
  public Integer version;

  public Protocol() {}
  public Protocol(String name, Integer version) { this.name = name; this.version = version; }
}
