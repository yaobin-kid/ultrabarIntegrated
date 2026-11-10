package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * 单个 A2UI 布局项：一个 layoutId 对应一份 UI 数据。
 * <p>
 * {@link #ui} 为 JSON 数组（{@code List<Map<String, Object>>}），即 A2UI 组件树的根结构。
 * 兼容旧调用方式：构造参数传 JSON 字符串时，SDK 内部自动解析为数组。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIItem {

    private static final TypeReference<List<Map<String, Object>>> UI_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() {
            };

    private static final TypeReference<Map<String, Object>> UI_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    /** 布局 id */
    public String layoutId;

    /** 布局名称（可选，用于展示/标识） */
    public String name;

    /** UI 数据，JSON 数组结构（组件树） */
    public List<Map<String, Object>> ui;

    public A2UIItem() {
    }

    public A2UIItem(String layoutId, List<Map<String, Object>> ui) {
        this.layoutId = layoutId;
        this.ui = ui;
    }

    public A2UIItem(String layoutId, String name, List<Map<String, Object>> ui) {
        this.layoutId = layoutId;
        this.name = name;
        this.ui = ui;
    }

    /**
     * 兼容旧用法：ui 为 JSON 字符串，内部解析为 JSON 数组。
     *
     * @throws IllegalArgumentException ui 不是合法的 JSON 数组
     */
    public A2UIItem(String layoutId, String uiJson) {
        this.layoutId = layoutId;
        this.ui = parseUi(uiJson);
    }

    /**
     * 兼容旧用法：ui 为 JSON 字符串，内部解析为 JSON 数组。
     *
     * @throws IllegalArgumentException ui 不是合法的 JSON 数组
     */
    public A2UIItem(String layoutId, String name, String uiJson) {
        this.layoutId = layoutId;
        this.name = name;
        this.ui = parseUi(uiJson);
    }

    private static List<Map<String, Object>> parseUi(String uiJson) {
        if (uiJson == null || uiJson.isEmpty()) {
            return null;
        }
        String trimmed = uiJson.trim();
        try {
            if (trimmed.startsWith("[")) {
                return Json.mapper().readValue(trimmed, UI_LIST_TYPE);
            }
            if (trimmed.startsWith("{")) {
                // 兼容单个 JSON 对象：自动包装为单元素数组
                Map<String, Object> single = Json.mapper().readValue(trimmed, UI_MAP_TYPE);
                List<Map<String, Object>> list = new java.util.ArrayList<Map<String, Object>>(1);
                list.add(single);
                return list;
            }
            throw new IllegalArgumentException("ui must be a JSON array or object");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("ui must be a valid JSON array: " + e.getMessage(), e);
        }
    }
}
