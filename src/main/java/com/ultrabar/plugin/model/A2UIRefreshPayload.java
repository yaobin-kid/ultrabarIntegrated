package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件端上报的 A2UI 视图刷新数据：刷新一个或多个已存在的 A2UI 视图。
 * 只携带 instanceId + layoutId + ui 用于定位并替换已渲染视图的内容；
 * 完整卡片定义走查询路径（a2ui_data_request → onA2UIDataRequest 返回 A2UIItem）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIRefreshPayload implements Payload {

    /** 待刷新的视图列表 */
    public List<A2UIRefreshItem> data;

    /** 数据产生时间戳（毫秒） */
    public Long timestamp;

    public A2UIRefreshPayload() {
    }

    public A2UIRefreshPayload(List<A2UIRefreshItem> data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
