package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterPayload implements Payload {

  public String id;
  public String name;
  public String version;
  public String packageName;
  public String signature;


  public RegisterPayload() {}
}
