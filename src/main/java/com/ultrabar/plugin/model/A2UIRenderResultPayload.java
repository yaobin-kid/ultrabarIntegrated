package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件端对 a2ui_render 渲染通知的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIRenderResultPayload extends ReplyPayload {

    public A2UIRenderResultPayload() {
    }

    public A2UIRenderResultPayload(boolean success) {
        this.success = success;
    }
}
