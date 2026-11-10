package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 消息上报：插件 → 服务端，由宿主按目的地透传。仅支持文本消息。
 * <p>
 * {@link #destination} 为消息透传的目的地，默认 {@link MessageDestination#ULTRA_WORKSPACE_PLUGIN}；
 * 其他透传场景只需换 destination 参数。
 * <p>
 * {@link #pluginId} 标识目标插件实例（由接收端定义与解释，如某个界面/场景实例），
 * {@link #message} 为要投递的文本内容。
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

    /** 文本消息内容 */
    public String message;

    public Long timestamp;

    public MessagePayload() {
    }

    public MessagePayload(String pluginId, String message) {
        this(MessageDestination.ULTRA_WORKSPACE_PLUGIN, pluginId, message);
    }

    public MessagePayload(MessageDestination destination, String pluginId, String message) {
        this.destination = destination;
        this.pluginId = pluginId;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
