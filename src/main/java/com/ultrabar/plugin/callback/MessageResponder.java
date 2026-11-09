package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.MessageResultPayload;
import com.ultrabar.plugin.model.MessageType;
import io.netty.channel.Channel;

/**
 * 插件端响应服务端文本消息的 responder。
 * 插件收到 {@code PluginListener#onMessage} 后必须调用
 * {@link #sendSuccess()} 或 {@link #sendError(String, String, boolean)} 之一。
 */
public class MessageResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;

    public MessageResponder(Channel channel, String requestId, ObjectMapper mapper,
                            String sessionId, String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    /** 发送成功响应 */
    public void sendSuccess() {
        MessageResultPayload payload = new MessageResultPayload();
        payload.success = true;
        write(payload);
    }

    /** 发送失败响应 */
    public void sendError(String code, String message, boolean retryable) {
        MessageResultPayload payload = new MessageResultPayload();
        payload.success = false;
        payload.error = ErrorInfo.of(code, message, retryable, null);
        write(payload);
    }

    private void write(MessageResultPayload payload) {
        EnvelopeWriter.write(
                channel,
                mapper,
                Envelope.of(MessageType.MESSAGE_RESULT, requestId, payload)
                        .withSession(sessionId, auth));
    }
}
