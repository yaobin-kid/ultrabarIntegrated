package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.AgenuiActionCallResultPayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.MessageType;
import io.netty.channel.Channel;

/**
 * 插件端响应服务端 agenui action call 的 responder。
 */
public class AgenuiActionCallResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;

    public AgenuiActionCallResponder(Channel channel, String requestId, ObjectMapper mapper) {
        this(channel, requestId, mapper, null, null);
    }

    public AgenuiActionCallResponder(Channel channel, String requestId, ObjectMapper mapper,
                                     String sessionId, String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    /** 发送成功响应，可携带返回数据 */
    public void sendSuccess(Object result) {
        AgenuiActionCallResultPayload payload = new AgenuiActionCallResultPayload();
        payload.success = true;
        payload.result = result;
        write(payload);
    }

    /** 发送失败响应 */
    public void sendError(String code, String message, boolean retryable) {
        AgenuiActionCallResultPayload payload = new AgenuiActionCallResultPayload();
        payload.success = false;
        payload.error = ErrorInfo.of(code, message, retryable, null);
        write(payload);
    }

    private void write(AgenuiActionCallResultPayload payload) {
        EnvelopeWriter.write(
                channel,
                mapper,
                Envelope.of(MessageType.AGENUI_ACTION_CALL_RESULT, requestId, payload)
                        .withSession(sessionId, auth));
    }
}
