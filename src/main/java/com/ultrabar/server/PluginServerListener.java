package com.ultrabar.server;

import com.ultrabar.plugin.model.EventPayload;
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

}
