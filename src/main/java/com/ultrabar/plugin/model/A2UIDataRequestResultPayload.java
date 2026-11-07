package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件对 a2ui 数据拉取请求的响应，携带当前的 a2ui 布局列表。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIDataRequestResultPayload extends ReplyPayload {

    /** 当前持有的 a2ui 布局项列表 */
    public List<A2UIItem> items;

    public A2UIDataRequestResultPayload() {
    }
}
