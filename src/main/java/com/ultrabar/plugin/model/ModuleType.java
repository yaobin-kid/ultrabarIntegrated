package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模块事件所属模块。
 */
public enum ModuleType {
    /** 播放器 */
    PLAYER("player"),

    /** 智能家居 / 开关 */
    SMART_HOME("smart_home");

    private final String wireName;

    ModuleType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static ModuleType fromWire(String value) {
        if (value != null) {
            for (ModuleType type : values()) {
                if (type.wireName.equalsIgnoreCase(value)) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException("unknown module: " + value);
    }
}
