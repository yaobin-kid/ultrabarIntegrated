package com.ultrabar.plugin.model;

/**
 * 服务端发起的动态卡片参数 schema 拉取请求。
 * 插件收到后应通过
 * {@link com.ultrabar.plugin.callback.PluginListener#onA2UIArgsSchemaRequest(String)}
 * 返回 {@code schemaGetter} 对应动作实时生成的参数 schema。
 */
public class A2UIArgsSchemaRequestPayload implements Payload {

    /** 要调用的 schemaGetter 动作 id（即 {@link A2UIArgs#schemaGetter()} 的值） */
    public String schemaGetter;

    public A2UIArgsSchemaRequestPayload() {
    }

    public A2UIArgsSchemaRequestPayload(String schemaGetter) {
        this.schemaGetter = schemaGetter;
    }
}
