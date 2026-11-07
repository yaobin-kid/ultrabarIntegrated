package com.ultrabar.server;

import com.ultrabar.plugin.model.A2UIDataPayload;
import com.ultrabar.plugin.model.A2UIUpdateDataModelPayload;
import com.ultrabar.plugin.model.EventPayload;
import com.ultrabar.plugin.model.ModuleEventPayload;
import com.ultrabar.plugin.model.ModuleInfo;

import java.util.Collections;
import java.util.List;

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
     * 插件端上报 A2UI 数据时触发。
     * 此时数据已存入 {@link PluginSession} 中。
     */
    default void onA2UIData(PluginSession session, A2UIDataPayload payload) {
    }

    /**
     * 插件端上报 A2UI updateDataModel 时触发。
     * 此时 dataModel 已存入 {@link PluginSession} 中。
     */
    default void onA2UIUpdateDataModel(PluginSession session, A2UIUpdateDataModelPayload payload) {
    }

    /**
     * 插件查询模块列表时触发。宿主实现此方法返回模块数据，
     * 在线/离线过滤由服务端统一完成。
     *
     * @param session 发起查询的插件会话
     * @return 模块列表；返回全部模块即可。默认返回空列表。
     */
    default List<ModuleInfo> onQueryModules(PluginSession session) {
        return Collections.emptyList();
    }

}
