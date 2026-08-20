package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.CallResultPayload;
import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.TaskInfo;
import io.netty.channel.Channel;

import java.util.Map;

public class CallResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;
    private final String sessionId;
    private final String auth;

    public CallResponder(Channel channel, String requestId, ObjectMapper mapper) {
        this(channel, requestId, mapper, null, null);
    }

    public CallResponder(Channel channel, String requestId, ObjectMapper mapper, String sessionId, String auth) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
        this.sessionId = sessionId;
        this.auth = auth;
    }

    public void sendSuccess(Map<String, Object> data) {
        CallResultPayload payload = new CallResultPayload();
        payload.success = true;
        payload.data = data;
        write(payload);
    }

    public void sendAccepted(Map<String, Object> data, String taskId, String statusUrl) {
        CallResultPayload payload = new CallResultPayload();
        payload.success = true;
        payload.accepted = true;
        payload.data = data;
        if (taskId != null || statusUrl != null) {
            payload.task = TaskInfo.pending(taskId);
            payload.task.statusUrl = statusUrl;
        }
        write(payload);
    }

    public void sendError(String code, String message, boolean retryable, Map<String, Object> details) {
        CallResultPayload payload = new CallResultPayload();
        payload.success = false;
        payload.error = ErrorInfo.of(code, message, retryable, details);
        write(payload);
    }

    private void write(CallResultPayload payload) {
        EnvelopeWriter.write(
                channel,
                mapper,
                Envelope.of(MessageType.CALL_RESULT, requestId, payload).withSession(sessionId, auth));
    }
}
