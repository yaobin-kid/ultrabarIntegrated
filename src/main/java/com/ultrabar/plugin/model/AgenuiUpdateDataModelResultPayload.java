package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端对 agenui_update_data_model 的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AGenUIUpdateDataModelResultPayload extends ReplyPayload {

    public AGenUIUpdateDataModelResultPayload() {
    }

    public AGenUIUpdateDataModelResultPayload(boolean success) {
        this.success = success;
    }
}
