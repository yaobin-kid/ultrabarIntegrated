package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus implements WireEnum {
    PENDING("pending"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String wireName;

    TaskStatus(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static TaskStatus fromWire(String wireName) {
        return Enums.fromWire(values(), wireName);
    }
}
