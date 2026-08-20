package com.ultrabar.server;

import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class SessionRegistry {
    private final ConcurrentHashMap<String, PluginSession> byPackage = new ConcurrentHashMap<String, PluginSession>();
    private final ConcurrentHashMap<String, PluginSession> bySessionId = new ConcurrentHashMap<String, PluginSession>();
    private final ConcurrentHashMap<Channel, PluginSession> byChannel = new ConcurrentHashMap<Channel, PluginSession>();

    PluginSession put(PluginSession session) {
        PluginSession previous = byPackage.put(session.packageName(), session);
        if (previous != null) {
            bySessionId.remove(previous.sessionId(), previous);
            Channel oldChannel = previous.channel();
            if (oldChannel != null) {
                byChannel.remove(oldChannel, previous);
                oldChannel.close();
            }
        }
        bySessionId.put(session.sessionId(), session);
        if (session.channel() != null) {
            byChannel.put(session.channel(), session);
        }
        return previous;
    }

    PluginSession byPackage(String packageName) {
        return packageName == null ? null : byPackage.get(packageName);
    }

    PluginSession bySessionId(String sessionId) {
        return sessionId == null ? null : bySessionId.get(sessionId);
    }

    PluginSession byChannel(Channel channel) {
        return channel == null ? null : byChannel.get(channel);
    }

    PluginSession resolve(Channel channel, String sessionId) {
        PluginSession byId = bySessionId(sessionId);
        if (byId != null) {
            return byId;
        }
        return byChannel(channel);
    }

    List<PluginSession> findByActionId(String actionId) {
        List<PluginSession> matches = new ArrayList<PluginSession>();
        for (PluginSession session : byPackage.values()) {
            if (session.hasAction(actionId)) {
                matches.add(session);
            }
        }
        return matches;
    }

    Collection<PluginSession> all() {
        return Collections.unmodifiableCollection(byPackage.values());
    }

    PluginSession removeIfBoundTo(Channel channel) {
        PluginSession session = byChannel.remove(channel);
        if (session == null) {
            return null;
        }
        if (session.channel() != null && session.channel() != channel) {
            return null;
        }
        byPackage.remove(session.packageName(), session);
        bySessionId.remove(session.sessionId(), session);
        return session;
    }
}
