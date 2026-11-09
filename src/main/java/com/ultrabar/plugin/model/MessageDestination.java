package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 消息透传目的地。
 */
public enum MessageDestination {

    /** Ultra WorkSpace PLUGIN 端（默认） */
    ULTRA_WORKSPACE_PLUGIN("ultra_workspace_plugin");


    private final String wireName;

    MessageDestination(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static MessageDestination fromWire(String wireName) {
        if (wireName != null) {
            for (MessageDestination destination : values()) {
                if (destination.wireName.equalsIgnoreCase(wireName)) {
                    return destination;
                }
            }
        }
        return null;
    }
}
