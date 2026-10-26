package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件注册模块事件。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleEventRegisterPayload implements Payload {
    public List<ModuleType> modules;

    public ModuleEventRegisterPayload() {
    }

    public ModuleEventRegisterPayload(List<ModuleType> modules) {
        this.modules = modules;
    }
}
