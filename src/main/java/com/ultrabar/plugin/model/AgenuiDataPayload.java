package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件端上报的 agenui 数据。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenuiDataPayload implements Payload {

    /** agenui 数据类型标识 */
    public String dataType;

    /** agenui 原始数据 */
    public Object data;

    /** 数据产生时间戳（毫秒） */
    public Long timestamp;

    public AgenuiDataPayload() {
    }

    public AgenuiDataPayload(String dataType, Object data) {
        this.dataType = dataType;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
}
