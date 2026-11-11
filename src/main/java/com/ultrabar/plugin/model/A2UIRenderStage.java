package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A2UI 渲染阶段：
 * <ul>
 *   <li>{@link #FIRST_RENDER} — 卡片首次渲染</li>
 *   <li>{@link #REFRESH} — 卡片刷新（内容更新后重新渲染）</li>
 * </ul>
 */
public enum A2UIRenderStage {

    FIRST_RENDER("first_render"),
    REFRESH("refresh");

    private final String wireName;

    A2UIRenderStage(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static A2UIRenderStage fromWire(String value) {
        if (value == null) {
            return null;
        }
        for (A2UIRenderStage s : values()) {
            if (s.wireName.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("unknown a2ui render stage: " + value);
    }
}
