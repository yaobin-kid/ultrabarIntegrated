package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件对动态参数 schema 拉取请求的响应：携带 {@code schemaGetter} 实时生成的 {@link A2UIArgs}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIArgsSchemaResultPayload extends ReplyPayload {

    /** 动态生成的参数 schema（参数名 → 参数描述），支持全部 6 种类型 */
    public A2UIArgs args;

    public A2UIArgsSchemaResultPayload() {
    }
}
