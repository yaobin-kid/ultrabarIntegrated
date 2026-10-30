package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterPayload implements Payload {

    public String name;
    public String packageName;

    public boolean isConfig = true;
    public String configBtnName;

    /** 是否支持 agenui 功能 */
    public boolean supportsAgenui = false;

    public RegisterPayload() {
    }
}
