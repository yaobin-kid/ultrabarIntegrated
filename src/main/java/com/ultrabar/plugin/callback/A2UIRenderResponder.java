package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.A2UIRenderResultPayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.MessageType;
import io.netty.channel.Channel;

/**
 * 插件端响应服务端 A2UI 渲染通知（a2ui_render）的 responder。
 */
public class A2UIRenderResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;

    public A2UIRenderResponder(Channel channel, String requestId, ObjectMapper mapper) {
        this(channel, requestId, mapper, null, null);
    }

    public A2UIRenderResponder(Channel channel, String requestId, ObjectMapper mapper,
                               String sessionId, String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    /** 发送成功响应 */
    public void sendSuccess() {
        write(new A2UIRenderResultPayload(true));
    }

    /** 发送失败响应 */
    public void sendError(String code, String message, boolean retryable) {
        A2UIRenderResultPayload payload = new A2UIRenderResultPayload();
        payload.success = false;
        payload.error = ErrorInfo.of(code, message, retryable, null);
        write(payload);
    }

    private void write(A2UIRenderResultPayload payload) {
        EnvelopeWriter.write(
                channel,
                mapper,
                Envelope.of(MessageType.A2UI_RENDER_RESULT, requestId, payload)
                        .withSession(sessionId, auth));
    }
}
