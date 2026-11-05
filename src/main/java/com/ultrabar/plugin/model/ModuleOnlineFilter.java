package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 模块在线状态过滤。
 */
public enum ModuleOnlineFilter {

    /** 仅在线模块 */
    ONLINE("online"),

    /** 仅离线模块 */
    OFFLINE("offline"),

    /** 全部模块（在线 + 离线） */
    ALL("all"),

    ;

    private final String wireName;

    ModuleOnlineFilter(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static ModuleOnlineFilter fromWire(String value) {
        if (value != null) {
            for (ModuleOnlineFilter filter : values()) {
                if (filter.wireName.equalsIgnoreCase(value)) {
                    return filter;
                }
            }
        }
        return ALL;
    }
}
