package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件端上报的 agenui updateDataModel 数据模型。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AGenUIUpdateDataModelPayload implements Payload {

    /** dataModel JSON 字符串 */
    public String dataModel;

    /** 增量更新标记：true 时 dataModel 只包含变更部分 */
    public Boolean incremental;

    /** 数据产生时间戳（毫秒） */
    public Long timestamp;

    public AGenUIUpdateDataModelPayload() {
    }

    public AGenUIUpdateDataModelPayload(String dataModel) {
        this.dataModel = dataModel;
        this.incremental = false;
        this.timestamp = System.currentTimeMillis();
    }
}
