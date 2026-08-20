package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OptionProvider implements WireEnum {
    STATIC("static"),
    REMOTE("remote");

    private final String wireName;

    OptionProvider(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static OptionProvider fromWire(String wireName) {
        return Enums.fromWire(values(), wireName);
    }
}
