package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端主动调用插件端的 agenui action。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AGenUIActionCallPayload implements Payload {

    /** 函数名 */
    public String actionName;

    /** 调用参数 JSON 字符串 */
    public String context;

    public AGenUIActionCallPayload() {
    }

    public AGenUIActionCallPayload(String actionName, String context) {
        this.actionName = actionName;
        this.context = context;
    }
}
