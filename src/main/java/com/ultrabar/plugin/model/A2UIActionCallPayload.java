package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端主动调用插件端的 a2ui action。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIActionCallPayload implements Payload {

    /** 函数名 */
    public String actionName;

    /** 调用参数 JSON 字符串 */
    public String context;

    /** 布局 id */
    public String layoutId;

    public A2UIActionCallPayload() {

    }

    public A2UIActionCallPayload(String actionName, String context, String layoutId) {
        this.actionName = actionName;
        this.context = context;
        this.layoutId = layoutId;
    }
}
