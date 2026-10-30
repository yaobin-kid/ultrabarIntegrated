package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端对 agenui_update_data_model 的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenuiUpdateDataModelResultPayload extends ReplyPayload {

    public AgenuiUpdateDataModelResultPayload() {
    }

    public AgenuiUpdateDataModelResultPayload(boolean success) {
        this.success = success;
    }
}
