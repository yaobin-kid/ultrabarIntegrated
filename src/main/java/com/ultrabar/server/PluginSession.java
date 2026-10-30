package com.ultrabar.server;

import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.Features;
import com.ultrabar.plugin.model.ModuleType;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.Topic;
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

    /** 最近一次收到的 agenui 数据（key=dataType） */
    private volatile Map<String, Object> agenuiData = Collections.emptyMap();

    /** 最近一次收到的 agenui dataModel（key=modelType） */
    private volatile Map<String, Object> agenuiDataModels = Collections.emptyMap();

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

    /** 注册时是否声明支持 agenui 功能 */
    public boolean supportsAgenui() {
        return plugin != null && plugin.supportsAgenui;
    }

    /** 获取当前已缓存的全部 agenui 数据快照 */
    public Map<String, Object> agenuiData() {
        return agenuiData;
    }

    /** 获取指定类型的 agenui 数据 */
    public Object agenuiData(String dataType) {
        return agenuiData.get(dataType);
    }

    /** 存储一份 agenui 数据 */
    void setAgenuiData(String dataType, Object data) {
        if (dataType == null) {
            return;
        }
        Map<String, Object> copy = new HashMap<String, Object>(agenuiData);
        copy.put(dataType, data);
        this.agenuiData = Collections.unmodifiableMap(copy);
        touch();
    }

    /** 获取当前已缓存的全部 agenui dataModel 快照 */
    public Map<String, Object> agenuiDataModels() {
        return agenuiDataModels;
    }

    /** 获取指定类型的 agenui dataModel */
    public Object agenuiDataModel(String modelType) {
        return agenuiDataModels.get(modelType);
    }

    /** 存储一份 agenui dataModel */
    void setAgenuiDataModel(String modelType, Object dataModel) {
        if (modelType == null) {
            return;
        }
        Map<String, Object> copy = new HashMap<String, Object>(agenuiDataModels);
        copy.put(modelType, dataModel);
        this.agenuiDataModels = Collections.unmodifiableMap(copy);
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
