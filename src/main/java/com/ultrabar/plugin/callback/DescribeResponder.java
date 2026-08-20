package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.DescribeResultPayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.MessageType;
import io.netty.channel.Channel;

import java.util.Map;

public class DescribeResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;

    public DescribeResponder(Channel channel, String requestId, ObjectMapper mapper) {
        this(channel, requestId, mapper, null, null);
    }

    public DescribeResponder(Channel channel, String requestId, ObjectMapper mapper, String sessionId, String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    public void sendSuccess(DescribeResultPayload payload) {
        EnvelopeWriter.write(
                channel,
                mapper,
                Envelope.of(MessageType.DESCRIBE_RESULT, requestId, payload).withSession(sessionId, auth));
    }

    public void sendError(String code, String message, boolean retryable, Map<String, Object> details) {
        DescribeResultPayload payload = new DescribeResultPayload();
        payload.success = false;
        payload.error = ErrorInfo.of(code, message, retryable, details);
        sendSuccess(payload);
    }
}
