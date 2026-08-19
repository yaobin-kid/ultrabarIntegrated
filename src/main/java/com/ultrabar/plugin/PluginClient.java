package com.ultrabar.plugin;

/*
 * Modifications: handle incoming `describe` messages and dispatch to PluginListener.onDescribe(...)
 */

import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.DescribePayload;

// rest of the file unchanged; only ClientHandler's channelRead0 will route "describe" to listener
