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
 * <p>
 * 可选地通过 {@link #args} 声明卡片参数 schema（见 {@link A2UIArgs}），
 * 宿主据此渲染参数配置表单；动态 schema 由 {@code PluginServer#queryA2UIArgsSchema} 拉取。
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

    /**
     * 卡片参数 schema（可选）。
     * <p>
     * 声明用户可配置哪些字段、用什么控件渲染；支持静态 schema、动态 schema
     * （{@link A2UIArgs#dynamic(String)}，渲染前回调插件取实时 schema）
     * 以及两者混用。为 null 表示该卡片没有可配置参数。
     */
    public A2UIArgs args;

    /** 仅用于反序列化，请使用 4 参构造器创建实例 */
    public A2UIItem() {
    }

    /**
     * 完整构造：4 个参数必须全部传入，ui 为结构化数组，不含参数 schema。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param ui       UI 数据（A2UI 消息序列）
     */
    public A2UIItem(String layoutId, String cardId, String name, List<Map<String, Object>> ui) {
        this(layoutId, cardId, name, ui, null);
    }

    /**
     * 完整构造：4 个必填参数 + 可选的参数 schema。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param ui       UI 数据（A2UI 消息序列）
     * @param args     卡片参数 schema，可为 null
     */
    public A2UIItem(String layoutId, String cardId, String name,
                    List<Map<String, Object>> ui, A2UIArgs args) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
        if (ui == null) {
            throw new IllegalArgumentException("ui must not be null");
        }
        this.ui = ui;
        this.args = args;
    }

    /**
     * 完整构造：4 个参数必须全部传入，ui 为 JSON 字符串，内部解析为 JSON 数组，不含参数 schema。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param uiJson   UI 数据（JSON 数组或单条 JSON 对象字符串）
     * @throws IllegalArgumentException ui 不是合法的 JSON
     */
    public A2UIItem(String layoutId, String cardId, String name, String uiJson) {
        this(layoutId, cardId, name, uiJson, null);
    }

    /**
     * 完整构造：4 个必填参数 + 可选的参数 schema，ui 为 JSON 字符串。
     *
     * @param layoutId 布局 id
     * @param cardId   卡片 id
     * @param name     布局名称
     * @param uiJson   UI 数据（JSON 数组或单条 JSON 对象字符串）
     * @param args     卡片参数 schema，可为 null
     * @throws IllegalArgumentException ui 不是合法的 JSON
     */
    public A2UIItem(String layoutId, String cardId, String name, String uiJson, A2UIArgs args) {
        this.layoutId = requireNonEmpty("layoutId", layoutId);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
        this.ui = parseUi(uiJson);
        this.args = args;
    }

    /**
     * 设置卡片参数 schema（链式，便于在构造器之后追加）。
     */
    public A2UIItem withArgs(A2UIArgs args) {
        this.args = args;
        return this;
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
