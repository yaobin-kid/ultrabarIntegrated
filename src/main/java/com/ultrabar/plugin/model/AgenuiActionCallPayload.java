package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 服务端主动调用插件端的 agenui action。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AGenUIActionCallPayload implements Payload {

    /** AGenUI action id */
    public String actionId;

    /** 调用参数 */
    public Map<String, Object> params;

    public AGenUIActionCallPayload() {
    }

    public AGenUIActionCallPayload(String actionId, Map<String, Object> params) {
        this.actionId = actionId;
        this.params = params;
    }
}
