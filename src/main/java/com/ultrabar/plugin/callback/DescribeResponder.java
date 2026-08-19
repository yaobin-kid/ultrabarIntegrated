package com.ultrabar.plugin.callback;

import com.ultrabar.plugin.model.DescribeResultPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper to send describe result back to server for a specific requestId.
 */
public class DescribeResponder {
  private final Channel channel;
  private final String requestId;
  private final ObjectMapper mapper;

  public DescribeResponder(Channel channel, String requestId, ObjectMapper mapper) {
    this.channel = channel;
    this.requestId = requestId;
    this.mapper = mapper;
  }

  /**
   * Send a successful describe result with typed payload.
   */
  public void sendSuccess(DescribeResultPayload payload) {
    try {
      Map<String, Object> env = new HashMap<>();
      env.put("type", "describe_result");
      env.put("requestId", requestId);
      env.put("timestamp", Instant.now().toString());
      env.put("payload", payload);
      String json = mapper.writeValueAsString(env);
      channel.writeAndFlush(json + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Send an error result for describe.
   */
  public void sendError(String code, String message, boolean retryable, Map<String, Object> details) {
    try {
      DescribeResultPayload rp = new DescribeResultPayload();
      rp.success = false;
      rp.error = new com.ultrabar.plugin.model.ErrorInfo();
      rp.error.code = code;
      rp.error.message = message;
      rp.error.retryable = retryable;
      rp.error.details = details;
      rp.error.timestamp = Instant.now().toString();

      Map<String, Object> env = new HashMap<>();
      env.put("type", "describe_result");
      env.put("requestId", requestId);
      env.put("timestamp", Instant.now().toString());
      env.put("payload", rp);
      String json = mapper.writeValueAsString(env);
      channel.writeAndFlush(json + "\n");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
