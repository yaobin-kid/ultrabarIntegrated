package com.ultrabar.plugin.model;

/**
 * 服务端发起的 a2ui 数据拉取请求。
 * 插件收到后应通过 {@link com.ultrabar.plugin.callback.PluginListener#onA2UIDataRequest()}
 * 返回当前持有的 a2ui 布局数据。
 */
public class A2UIDataRequestPayload implements Payload {
}
