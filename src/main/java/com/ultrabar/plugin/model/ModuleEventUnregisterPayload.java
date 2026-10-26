package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件释放模块事件注册。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleEventUnregisterPayload implements Payload {
    public List<ModuleType> modules;

    public ModuleEventUnregisterPayload() {
    }

    public ModuleEventUnregisterPayload(List<ModuleType> modules) {
        this.modules = modules;
    }
}
