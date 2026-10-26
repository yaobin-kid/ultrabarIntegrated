package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端透传给插件的统一模块事件消息。
 * <p>
 * appState、timestamp、event 分别表示目标应用当前前后台状态、事件时间戳和模块事件原始消息。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleEventPayload implements Payload {
    public static final String APP_STATE_FOREGROUND = "foreground";
    public static final String APP_STATE_BACKGROUND = "background";
    public static final String APP_STATE_NONE = "none";

    /**
     * 事件所属模块
     */
    public ModuleType module;

    /**
     * 模块实例 id，用于区分同一模块类型下的不同设备/实例
     */
    public String moduleId;

    /**
     * 模块内的事件动作，如 play / pause
     */
    public String action;

    /**
     * 模块事件产生时间（毫秒）
     */
    public Long timestamp;

    /**
     * 目标应用当前状态：foreground / background / none
     */
    public String appState;



    /**
     * 模块事件原始消息
     */
    public Object event;

    public ModuleEventPayload() {
    }

    /**
     * 为某一个目标应用复制一份事件，仅替换 appState。
     */
    public ModuleEventPayload copyFor(String appState) {
        ModuleEventPayload copy = new ModuleEventPayload();
        copy.module = module;
        copy.moduleId = moduleId;
        copy.action = action;
        copy.timestamp = timestamp;
        copy.appState = appState;
        copy.event = event;
        return copy;
    }
}
