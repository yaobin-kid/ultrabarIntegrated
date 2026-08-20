package com.ultrabar.server;

import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;

/**
 * External registration policy. The server writes the returned payload to the plugin
 * and creates a {@link PluginSession} only when {@code success} is true.
 */
public interface PluginRegisterHandler {
    RegisterResultPayload handleRegister(RegisterPayload request) throws Exception;
}
