package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 单个 A2UI 布局项：一个 layoutId 对应一份 UI JSON 数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIItem {

    /** 布局 id */
    public String layoutId;

    /** 布局名称（可选，用于展示/标识） */
    public String name;

    /** UI JSON 数据字符串 */
    public String ui;

    public A2UIItem() {
    }

    public A2UIItem(String layoutId, String ui) {
        this.layoutId = layoutId;
        this.ui = ui;
    }

    public A2UIItem(String layoutId, String name, String ui) {
        this.layoutId = layoutId;
        this.name = name;
        this.ui = ui;
    }
}
