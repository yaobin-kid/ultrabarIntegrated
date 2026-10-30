package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件端上报的 AGenUI 数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AGenUIDataPayload implements Payload {

    /** AGenUI 数据 JSON 字符串 */
    public String data;

    /** 数据产生时间戳（毫秒） */
    public Long timestamp;

    public AGenUIDataPayload() {
    }

    public AGenUIDataPayload(String data) {
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
