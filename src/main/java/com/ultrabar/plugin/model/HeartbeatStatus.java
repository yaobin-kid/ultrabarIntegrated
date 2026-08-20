package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum HeartbeatStatus implements WireEnum {
    ALIVE("alive");

    private final String wireName;

    HeartbeatStatus(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static HeartbeatStatus fromWire(String wireName) {
        return Enums.fromWire(values(), wireName);
    }
}
