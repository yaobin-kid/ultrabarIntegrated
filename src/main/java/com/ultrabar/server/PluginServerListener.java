package com.ultrabar.server;

import com.ultrabar.plugin.model.TaskUpdatePayload;

public interface PluginServerListener {
    default void onRegistered(PluginSession session) {}

    default void onUnregistered(PluginSession session) {}

    default void onActionsUpdated(PluginSession session) {}

    default void onTaskUpdate(PluginSession session, TaskUpdatePayload payload) {}
}
