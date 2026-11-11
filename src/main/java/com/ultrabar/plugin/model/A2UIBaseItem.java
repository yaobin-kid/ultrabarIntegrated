package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.Map;

/**
 * A2UI 布局项基类：抽取 {@link A2UIItem} 与 {@link A2UIRefreshItem} 的公共属性
 * {@link #layoutId} 和 {@link #ui}，以及 JSON 字符串解析工具方法。
 * <p>
 * 不直接实例化，请使用子类：
 * <ul>
 *   <li>{@link A2UIItem} — 完整卡片定义（查询路径：onA2UIDataRequest / queryA2UIData）</li>
 *   <li>{@link A2UIRefreshItem} — 已存在视图的刷新项（refreshA2UI）</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class A2UIBaseItem {

    static final TypeReference<List<Map<String, Object>>> UI_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() {
            };

    static final TypeReference<Map<String, Object>> UI_MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    /** 布局 id */
    public String layoutId;

    /** UI 数据，JSON 数组结构（A2UI 消息序列） */
    public List<Map<String, Object>> ui;

    protected A2UIBaseItem() {
    }

    protected A2UIBaseItem(String layoutId, List<Map<String, Object>> ui) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        if (ui == null) {
            throw new IllegalArgumentException("ui must not be null");
        }
        this.ui = ui;
    }

    protected A2UIBaseItem(String layoutId, String uiJson) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        this.ui = parseUi(uiJson);
    }

    static String requireNonEmpty(String key, String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be empty");
        }
        return value;
    }

    static List<Map<String, Object>> parseUi(String uiJson) {
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
