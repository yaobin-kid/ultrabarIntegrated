package com.ultrabar.server;

import com.ultrabar.plugin.model.*;
import io.netty.channel.Channel;

import java.util.*;
import java.util.stream.Collectors;

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
    private volatile Set<Topic> topics;
    private volatile long revision;

    private volatile long lastSeenMillis = System.currentTimeMillis();
    private volatile Set<ModuleType> moduleEvents = Collections.emptySet();
    private volatile boolean foreground;
    private volatile long foregroundAtMillis;

    /**
     * 最近一次收到的 agenui 数据 JSON 字符串
     */
    private volatile String aGenUIData;

    /**
     * 最近一次收到的 agenui dataModel JSON 字符串
     */
    private volatile String aGenUIDataModel;

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


    public String toSurfaceId() {
        StringBuilder builder = new StringBuilder("agenui_");
        for (int i = 0; i < packageName.length(); i++) {
            char c = packageName.charAt(i);
            builder.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return builder.toString();
    }

    public List<ActionSummary> actions(Features... features) {
        return actions().stream().filter(f -> f.supportsAll(features))
                .collect(Collectors.toList());
    }

    public long revision() {
        return revision;
    }

    public long lastSeenMillis() {
        return lastSeenMillis;
    }

    public boolean hasAction(String actionId) {
        if (actionId == null) return false;
        List<ActionSummary> current = actions;
        for (int i = 0; i < current.size(); i++) {
            ActionSummary action = current.get(i);
            if (action != null && actionId.equals(action.actionId)) {
                return true;
            }
        }
        return false;
    }


    public boolean hasFeatures(String actionId, Features... features) {
        if (actionId == null) return false;
        List<ActionSummary> current = actions;
        for (int i = 0; i < current.size(); i++) {
            ActionSummary action = current.get(i);
            if (action != null && actionId.equals(action.actionId)) {
                if (action.supportsAll(features)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<ModuleType> moduleEvents() {
        return moduleEvents;
    }

    public boolean handlesModuleEvent(ModuleType module) {
        return module != null && moduleEvents.contains(module);
    }

    void registerModuleEvents(Collection<ModuleType> modules) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        Set<ModuleType> copy = new HashSet<ModuleType>(moduleEvents);
        for (ModuleType module : modules) {
            if (module != null) {
                copy.add(module);
            }
        }
        this.moduleEvents = Collections.unmodifiableSet(copy);
        touch();
    }

    void unregisterModuleEvents(Collection<ModuleType> modules) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        Set<ModuleType> copy = new HashSet<ModuleType>(moduleEvents);
        copy.removeAll(modules);
        this.moduleEvents = Collections.unmodifiableSet(copy);
        touch();
    }

    /**
     * 注册时是否声明支持 agenui 功能
     */
    public boolean supportsAGenUI() {
        return plugin != null && plugin.supportsAGenUI;
    }

    /**
     * 获取最近一次收到的 agenui 数据 JSON 字符串
     */
    public String aGenUIData() {
        return aGenUIData;
    }

    /**
     * 存储一份 agenui 数据 JSON 字符串
     */
    void setAGenUIData(String data) {
        if (data == null) {
            return;
        }
        this.aGenUIData = data;
        touch();
    }

    /**
     * 获取最近一次收到的 agenui dataModel JSON 字符串
     */
    public String aGenUIDataModel() {
        return aGenUIDataModel;
    }

    /**
     * 存储一份 agenui dataModel JSON 字符串
     */
    void setAGenUIDataModel(String dataModel) {
        if (dataModel == null) {
            return;
        }
        this.aGenUIDataModel = dataModel;
        touch();
    }

    public boolean isForeground() {
        return foreground;
    }

    public long foregroundAtMillis() {
        return foregroundAtMillis;
    }

    void setForeground(boolean foreground) {
        this.foreground = foreground;
        if (foreground) {
            this.foregroundAtMillis = System.currentTimeMillis();
        }
        touch();
    }

    void copyAppStateFrom(PluginSession other) {
        if (other == null) {
            return;
        }
        this.foreground = other.foreground;
        this.foregroundAtMillis = other.foregroundAtMillis;
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

    void updateTopic(Set<Topic> topics) {
        if (topics == null) {
            this.topics = Collections.emptySet();
        } else {
            this.topics = Collections.unmodifiableSet(new HashSet<>(topics));
        }

        touch();
    }
}
