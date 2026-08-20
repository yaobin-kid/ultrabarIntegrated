package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ParameterType implements WireEnum {
    TEXT("text"),
    TEXTAREA("textarea"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    SELECT("select"),
    MULTI_SELECT("multi_select");

    private final String wireName;

    ParameterType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static ParameterType fromWire(String wireName) {
        return Enums.fromWire(values(), wireName);
    }
}
