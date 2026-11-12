package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 消息上报：插件 → 服务端，由宿主按目的地透传。
 * <p>
 * {@link #destination} 为消息透传的目的地，默认 {@link MessageDestination#ULTRA_WORKSPACE_PLUGIN}；
 * 其他透传场景只需换 destination 参数。
 * <p>
 * {@link #pluginId} 标识目标插件实例（由接收端定义与解释，如某个界面/场景实例），
 * {@link #message} 为要投递的消息内容，支持任意 Jackson 可序列化对象
 * （String / Map / List / 自定义 POJO 等）；字符串按原样传输。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessagePayload implements Payload {

    /**
     * 消息透传目的地。仅插件→服务端方向使用：
     * 通过构造器创建时默认 {@link MessageDestination#ULTRA_WORKSPACE_PLUGIN}；
     * 服务端→插件方向为 null。
     */
    public MessageDestination destination;

    /** 目标插件实例 id */
    public String pluginId;

    /** 消息内容，支持任意 Jackson 可序列化对象（String / Map / List / POJO 等） */
    public Object message;

    public Long timestamp;

    public MessagePayload() {
    }

    public MessagePayload(String pluginId, Object message) {
        this(MessageDestination.ULTRA_WORKSPACE_PLUGIN, pluginId, message);
    }

    public MessagePayload(MessageDestination destination, String pluginId, Object message) {
        this.destination = destination;
        this.pluginId = pluginId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
