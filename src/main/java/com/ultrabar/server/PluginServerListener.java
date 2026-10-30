package com.ultrabar.server;

import com.ultrabar.plugin.model.AGenUIDataPayload;
import com.ultrabar.plugin.model.AGenUIUpdateDataModelPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.ModuleEventPayload;

public interface PluginServerListener {
    default void onRegistered(PluginSession session) {
    }

    default void onUnregistered(PluginSession session) {
    }

    default void onActionsUpdated(PluginSession session) {
    }

    default void onEventReceived(PluginSession session, EventPayload event) {

    }

    /**
     * 模块事件没有被任何插件消费时的兜底回调。
     */
    default void onModuleEventFallback(ModuleEventPayload event) {

    }

    /**
     * 插件端上报 AGenUI 数据时触发。
     * 此时数据已存入 {@link PluginSession} 中。
     */
    default void onAGenUIData(PluginSession session, AGenUIDataPayload payload) {
    }

    /**
     * 插件端上报 AGenUI updateDataModel 时触发。
     * 此时 dataModel 已存入 {@link PluginSession} 中。
     */
    default void onAGenUIUpdateDataModel(PluginSession session, AGenUIUpdateDataModelPayload payload) {
    }

}
