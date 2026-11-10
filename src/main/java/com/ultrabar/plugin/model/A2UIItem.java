package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * 单个 A2UI 布局项：一个 layoutId 对应一份 UI 数据。
 * <p>
 * {@link #ui} 为 JSON 数组（{@code List<Map<String, Object>>}），即 A2UI 消息序列。
 * 创建对象时 4 个参数（layoutId / name / cardId / ui）必须全部传入；
 * {@code ui} 支持两种重载：直接传结构化数组，或传 JSON 字符串由 SDK 内部解析。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIItem {

    private static final TypeReference<List<Map<String, Object>>> UI_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() {
            };

    private static final TypeReference<Map<String, Object>> UI_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    /** 布局 id（必填） */
    public String layoutId;

    /** 布局名称（必填，用于展示/标识） */
    public String name;

    /** 卡片 id（必填） */
    public String cardId;

    /** UI 数据，JSON 数组结构（A2UI 消息序列，必填） */
    public List<Map<String, Object>> ui;

    /** 仅用于反序列化，请使用 4 参构造器创建实例 */
    public A2UIItem() {
    }

    /**
     * 完整构造：4 个参数必须全部传入，ui 为结构化数组。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param ui       UI 数据（A2UI 消息序列）
     */
    public A2UIItem(String layoutId,String cardId, String name,  List<Map<String, Object>> ui) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
        if (ui == null) {
            throw new IllegalArgumentException("ui must not be null");
        }
        this.ui = ui;
    }

    /**
     * 完整构造：4 个参数必须全部传入，ui 为 JSON 字符串，内部解析为 JSON 数组。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param uiJson   UI 数据（JSON 数组或单条 JSON 对象字符串）
     * @throws IllegalArgumentException ui 不是合法的 JSON
     */
    public A2UIItem(String layoutId,String cardId, String name,  String uiJson) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
        this.ui = parseUi(uiJson);
    }

    private static String requireNonEmpty(String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        return value;
    }

    private static List<Map<String, Object>> parseUi(String uiJson) {
        if (uiJson == null || uiJson.isEmpty()) {
            throw new IllegalArgumentException("ui must not be empty");
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
