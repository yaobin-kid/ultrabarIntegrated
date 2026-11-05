package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模块事件所属模块。
 */
public enum ModuleType {

    /**
     * 按键 / 旋钮事件
     */
    DOT_KEY("DOT_KEY"),

    KNOB_KEY("KNOB_KEY"),

    SENSE_CUBE("SENSE_CUBE"),
    VIVO_CUBE("VIVO_CUBE"),



    ;

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
