package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 服务端主动调用插件端的 a2ui action。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIActionCallPayload implements Payload {

    /** 函数名 */
    public String actionName;

    /** 布局 id */
    public String layoutId;

    /** 卡片 id */
    public String cardId;

    /** 调用参数 */
    public Map<String, Object> context;

    public A2UIActionCallPayload() {

    }

    public A2UIActionCallPayload(String actionName, String layoutId, String cardId,
                                 Map<String, Object> context) {
        this.actionName = actionName;
        this.layoutId = layoutId;
        this.cardId = cardId;
        this.context = context;
    }
}
