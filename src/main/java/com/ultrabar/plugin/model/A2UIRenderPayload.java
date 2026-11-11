package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 服务端下发的 A2UI 渲染通知（{@code a2ui_render}）：
 * 卡片首次渲染或刷新渲染时，服务端调用本消息通知插件。
 * <p>
 * 插件端通过 {@code PluginListener.onA2UIRender} 接收，
 * 并通过 {@code A2UIRenderResponder} 回 ack。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIRenderPayload implements Payload {

    /** 卡片实例 id */
    public String instanceId;

    /** 布局 id */
    public String layoutId;

    /** 卡片 id */
    public String cardId;

    /** 渲染时生效的 args 参数值（参数名 → 当前值），可为 null */
    public Map<String, Object> args;

    /** 渲染阶段：first_render / refresh */
    public A2UIRenderStage stage;

    public A2UIRenderPayload() {
    }

    public A2UIRenderPayload(String instanceId, String layoutId, String cardId,
                             Map<String, Object> args, A2UIRenderStage stage) {
        this.instanceId = instanceId;
        this.layoutId = layoutId;
        this.cardId = cardId;
        this.args = args;
        this.stage = stage;
    }
}
