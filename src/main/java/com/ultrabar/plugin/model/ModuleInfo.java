package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 一个模块实例的描述。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleInfo {

    /**
     * 模块名称
     */
    public String name;

    /**
     * 模块串号
     */
    public String sn;

    /**
     * 模块 id
     */
    public String moduleId;

    /**
     * 模块类型
     */
    public ModuleType module;

    /**
     * 模块状态：true 在线，false 离线
     */
    public boolean online;


    public ModuleInfo() {
    }

    public ModuleInfo(String name, String sn, String moduleId, ModuleType module, boolean online) {
        this.name = name;
        this.sn = sn;
        this.moduleId = moduleId;
        this.module = module;
        this.online = online;
    }
}
