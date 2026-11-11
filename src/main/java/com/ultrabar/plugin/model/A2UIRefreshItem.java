package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * 单个已存在 A2UI 视图的刷新项：通过 instanceId + layoutId 定位到已渲染的视图，
 * 用新的 {@link #ui} 数据替换其内容。
 * <p>
 * 与 {@link A2UIItem} 的区别：A2UIItem 是完整的卡片定义（查询用，含 name/cardId/args），
 * 本类只用于刷新已存在的视图，3 个参数（instanceId / layoutId / ui）必须全部传入。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIRefreshItem extends A2UIBaseItem {

    /** 实例 id（必填，定位已存在的卡片实例） */
    public String instanceId;

    /** 仅用于反序列化，请使用 3 参构造器创建实例 */
    public A2UIRefreshItem() {
    }

    /**
     * @param instanceId 实例 id
     * @param layoutId   布局 id
     * @param ui         新的 UI 数据（A2UI 消息序列）
     */
    public A2UIRefreshItem(String instanceId, String layoutId, List<Map<String, Object>> ui) {
        super(layoutId, ui);
        this.instanceId = requireNonEmpty("instanceId", instanceId);
    }

    /**
     * @param instanceId 实例 id
     * @param layoutId   布局 id
     * @param uiJson     新的 UI 数据（JSON 数组或单条 JSON 对象字符串）
     * @throws IllegalArgumentException ui 不是合法的 JSON
     */
    public A2UIRefreshItem(String instanceId, String layoutId, String uiJson) {
        super(layoutId, uiJson);
        this.instanceId = requireNonEmpty("instanceId", instanceId);
    }
}
