package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件端对 agenui_action_call 的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIActionCallResultPayload extends ReplyPayload {

    /** action 执行返回数据 */
    public Object result;

    public A2UIActionCallResultPayload() {
    }

    public A2UIActionCallResultPayload(boolean success, Object result) {
        this.success = success;
        this.result = result;
    }
}
