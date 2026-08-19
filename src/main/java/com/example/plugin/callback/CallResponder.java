package com.example.plugin.callback;

import com.example.plugin.model.ErrorInfo;
import com.example.plugin.model.ResultPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper to send result/error back to server for a specific requestId.
 */
public class CallResponder {
  private final Channel channel;
  private final String requestId;
  private final ObjectMapper mapper;

  public CallResponder(Channel channel, String requestId, ObjectMapper mapper) {
    this.channel = channel;
    this.requestId = requestId;
    this.mapper = mapper;
  }

  /**
   * Send a successful result with optional data map.
   */
  public void sendSuccess(Map<String, Object> data) {
    ResultPayload rp = new ResultPayload();
    rp.success = true;
    rp.data = data;
    writeResult(rp);
  }

  /**
   * Send an accepted async result with task info (use data map to include taskId/statusUrl if desired).
   */
  public void sendAccepted(Map<String, Object> data, String taskId, String statusUrl) {
    ResultPayload rp = new ResultPayload();
    rp.success = true;
    rp.accepted = true;
    rp.data = data;
    if (taskId != null || statusUrl != null) {
      rp.task = new com.example.plugin.model.TaskInfo();
      rp.task.taskId = taskId;
      rp.task.statusUrl = statusUrl;
      rp.task.status = "pending";
    }
    writeResult(rp);
  }

  /**
   * Send an error result.
   */
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

  private void writeResult(ResultPayload rp) {
    try {
      // Build envelope manually to include type/requestId/payload
      Map<String, Object> env = new HashMap<>();
      env.put("type", "result");
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
