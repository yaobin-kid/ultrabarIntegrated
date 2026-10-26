package com.ultrabar.server;

import com.ultrabar.plugin.model.ModuleEventPayload;

/**
 * 模块事件兜底回调。当所有已订阅该模块的插件都没有消费事件时触发。
 */
@FunctionalInterface
public interface ModuleEventFallbackHandler {

    /**
     * 处理未被任何插件消费的模块事件。
     *
     * @param event 未消费的模块事件（appState 为 {@link com.ultrabar.plugin.model.ModuleEventPayload#APP_STATE_NONE}）
     */
    void onModuleEventFallback(ModuleEventPayload event);
}
