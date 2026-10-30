package com.ultrabar.server;

import com.ultrabar.plugin.model.AgenuiDataPayload;
import com.ultrabar.plugin.model.AgenuiUpdateDataModelPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.ModuleEventPayload;
import com.ultrabar.plugin.model.ReportPayload;
import com.ultrabar.plugin.model.TaskUpdatePayload;

public interface PluginServerListener {
    default void onRegistered(PluginSession session) {
    }

    default void onUnregistered(PluginSession session) {
    }

    default void onActionsUpdated(PluginSession session) {
    }

    default void onTaskUpdate(PluginSession session, TaskUpdatePayload payload) {
    }


    default void onReport(PluginSession session, ReportPayload payload) {

    }

    default void onEventReceived(PluginSession session, EventPayload event) {

    }

    /**
     * 模块事件没有被任何插件消费时的兜底回调。
     */
    default void onModuleEventFallback(ModuleEventPayload event) {

    }

    /**
     * 插件端上报 agenui 数据时触发。
     * 此时数据已存入 {@link PluginSession} 中。
     */
    default void onAgenuiData(PluginSession session, AgenuiDataPayload payload) {
    }

    /**
     * 插件端上报 agenui updateDataModel 时触发。
     * 此时 dataModel 已存入 {@link PluginSession} 中。
     */
    default void onAgenuiUpdateDataModel(PluginSession session, AgenuiUpdateDataModelPayload payload) {
    }

}
