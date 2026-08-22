package com.ultrabar.server;

import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.RegisterPayload;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One connected plugin, unique by {@code packageName}.
 */
public final class PluginSession {
    private final String packageName;
    private final String sessionId;
    private final String sessionToken;
    private final RegisterPayload plugin;
    private volatile Channel channel;
    private final String name;
    private volatile List<ActionSummary> actions = Collections.emptyList();
    private volatile long revision;

    private volatile long lastSeenMillis = System.currentTimeMillis();

    private final int port;


    PluginSession(String name, String packageName, String sessionId, String sessionToken, RegisterPayload plugin, Channel channel, int port) {
        this.name = name;
        this.packageName = packageName;
        this.sessionId = sessionId;
        this.sessionToken = sessionToken;
        this.plugin = plugin;
        this.channel = channel;
        this.port = port;
    }

    public String name() {
        return name;
    }

    public long port() {
        return port;
    }

    public String packageName() {
        return packageName;
    }

    public String sessionId() {
        return sessionId;
    }

    public String sessionToken() {
        return sessionToken;
    }

    public RegisterPayload plugin() {
        return plugin;
    }

    public Channel channel() {
        return channel;
    }

    public List<ActionSummary> actions() {
        return actions;
    }

    public long revision() {
        return revision;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public boolean hasAction(String actionId) {
        if (actionId == null) {
            return false;
        }
        List<ActionSummary> current = actions;
        for (int i = 0; i < current.size(); i++) {
            ActionSummary action = current.get(i);
            if (action != null && actionId.equals(action.actionId)) {
                return true;
            }
        }
        return false;
    }

    void touch() {
        lastSeenMillis = System.currentTimeMillis();
    }

    void bind(Channel channel) {
        this.channel = channel;
        touch();
    }

    void updateActions(List<ActionSummary> incoming, Long clientRevision) {
        if (incoming == null) {
            this.actions = Collections.emptyList();
        } else {
            this.actions = Collections.unmodifiableList(new ArrayList<ActionSummary>(incoming));
        }
        if (clientRevision != null) {
            this.revision = clientRevision.longValue();
        } else {
            this.revision++;
        }
        touch();
    }
}
