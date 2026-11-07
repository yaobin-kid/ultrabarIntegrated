package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端对 a2ui_data 的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIDataResultPayload extends ReplyPayload {

    public A2UIDataResultPayload() {
    }

    public A2UIDataResultPayload(boolean success) {
        this.success = success;
    }
}
