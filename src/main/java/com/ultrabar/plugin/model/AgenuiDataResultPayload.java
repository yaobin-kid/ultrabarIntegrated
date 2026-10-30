package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端对 agenui_data 的响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgenuiDataResultPayload extends ReplyPayload {

    public AgenuiDataResultPayload() {
    }

    public AgenuiDataResultPayload(boolean success) {
        this.success = success;
    }
}
