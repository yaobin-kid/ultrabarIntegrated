package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterPayload implements Payload {

  public String name;
  public String packageName;


  public RegisterPayload() {}
}
