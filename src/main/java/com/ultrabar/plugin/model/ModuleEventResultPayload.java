package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件对模块事件的消费结果。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModuleEventResultPayload extends ReplyPayload {
    /** 插件是否消费了该事件 */
    public Boolean consumed;

    /** 未消费原因，便于排查 */
    public String reason;
}
