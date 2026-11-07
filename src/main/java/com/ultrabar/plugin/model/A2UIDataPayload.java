package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件端上报的 A2UI 数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIDataPayload implements Payload {

    /** A2UI 布局项列表 */
    public List<A2UIItem> data;

    /** 数据产生时间戳（毫秒） */
    public Long timestamp;

    public A2UIDataPayload() {
    }

    public A2UIDataPayload(List<A2UIItem> data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
