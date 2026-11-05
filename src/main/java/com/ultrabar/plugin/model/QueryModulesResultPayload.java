package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 插件查询模块列表响应。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryModulesResultPayload extends ReplyPayload {

    /**
     * 模块列表
     */
    public List<ModuleInfo> modules;

    public QueryModulesResultPayload() {
    }
}
