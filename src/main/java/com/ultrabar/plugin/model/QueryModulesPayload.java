package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 插件查询模块列表请求。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryModulesPayload implements Payload {

    /**
     * 在线状态过滤，缺省为 {@link ModuleOnlineFilter#ALL}
     */
    public ModuleOnlineFilter filter;

    public QueryModulesPayload() {
    }

    public QueryModulesPayload(ModuleOnlineFilter filter) {
        this.filter = filter;
    }
}
