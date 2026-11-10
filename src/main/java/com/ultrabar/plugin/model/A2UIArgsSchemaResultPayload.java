package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 插件对动态参数 schema 拉取请求的响应：携带 {@code schemaGetter} 实时生成的参数 schema。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIArgsSchemaResultPayload extends ReplyPayload {

    /** 动态生成的参数 schema（参数名 → 参数描述），支持全部 6 种类型 */
    public Map<String, A2UIArgSpec> schema;

    public A2UIArgsSchemaResultPayload() {
    }
}
