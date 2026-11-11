package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 单个 A2UI 卡片定义项：一个 layoutId 对应一份 UI 数据。
 * <p>
 * 用于查询路径：插件在 {@code PluginListener#onA2UIDataRequest()} 返回本类列表，
 * 服务端通过 {@code PluginServer#queryA2UIData} 拉取并缓存到 {@code PluginSession}。
 * 创建对象时 4 个参数（layoutId / name / cardId / ui）必须全部传入；
 * {@code ui} 支持两种重载：直接传结构化数组，或传 JSON 字符串由 SDK 内部解析。
 * <p>
 * 可选地通过 {@link #args} 声明卡片参数 schema（见 {@link A2UIArgs}），
 * 宿主据此渲染参数配置表单；动态 schema 由 {@code PluginServer#queryA2UIArgsSchema} 拉取。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIItem extends A2UIBaseItem {

    /** 布局名称（必填，用于展示/标识） */
    public String name;

    /** 卡片 id（必填） */
    public String cardId;

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
        super(layoutId, ui);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
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
        super(layoutId, uiJson);
        this.name = requireNonEmpty("name", name);
        this.cardId = requireNonEmpty("cardId", cardId);
        this.args = args;
    }

    /**
     * 设置卡片参数 schema（链式，便于在构造器之后追加）。
     */
    public A2UIItem withArgs(A2UIArgs args) {
        this.args = args;
        return this;
    }
}
