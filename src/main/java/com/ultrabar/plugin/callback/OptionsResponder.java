package com.ultrabar.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultrabar.plugin.model.ErrorInfo;
import com.ultrabar.plugin.model.OptionsResult;
import com.ultrabar.plugin.model.ResultPayload;
import io.netty.channel.Channel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class OptionsResponder {
    private final Channel channel;
    private final String requestId;
    private final ObjectMapper mapper;

    public OptionsResponder(Channel channel, String requestId, ObjectMapper mapper) {
        this.channel = channel;
        this.requestId = requestId;
        this.mapper = mapper;
    }

    public void sendError(String code, String message, boolean retryable, Map<String, Object> details) {
        ResultPayload rp = new ResultPayload();
        rp.success = false;
        ErrorInfo ei = new ErrorInfo();
        ei.code = code;
        ei.message = message;
        ei.retryable = retryable;
        ei.details = details;
        ei.timestamp = java.time.Instant.now().toString();
        rp.error = ei;
        writeResult(rp);
    }

    public void sendSuccess(OptionsResult payload) {
        try {
            Map<String, Object> env = new HashMap<>();
            env.put("type", "options_result");
            env.put("requestId", requestId);
            env.put("timestamp", Instant.now().toString());
            env.put("payload", payload);
            String json = mapper.writeValueAsString(env);
            channel.writeAndFlush(json + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeResult(Object rp) {
        try {
            // Build envelope manually to include type/requestId/payload
            Map<String, Object> env = new HashMap<>();
            env.put("type", "options_result");
            env.put("requestId", requestId);
            env.put("timestamp", java.time.Instant.now().toString());
            env.put("payload", rp);
            String json = mapper.writeValueAsString(env);
            // Ensure newline framing (server expects \n)
            channel.writeAndFlush(json + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
