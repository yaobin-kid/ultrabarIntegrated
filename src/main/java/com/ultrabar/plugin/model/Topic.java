package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Topic {
    CPU("pc.cpu")
    ;
    private final String topic;

    Topic(String topic) {
        this.topic = topic;
    }
    // 核心：加上这个注解
    @JsonValue
    public String getTopic() {
        return topic;
    }
}
