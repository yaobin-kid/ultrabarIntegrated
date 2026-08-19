package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.ActionsCallback;
import com.ultrabar.plugin.callback.ActionsUpdateCallback;
import com.ultrabar.plugin.callback.RegisterCallback;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.PluginInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class ExampleMain {
  public static void main(String[] args) throws Exception {
    PluginClient client = new PluginClient("127.0.0.1", 39001);
    client.startAsync().thenRun(() -> System.out.println("Connected (startAsync completed)"));

    ObjectMapper mapper = new ObjectMapper();

    ActionSummary a1 = new ActionSummary();
    a1.id = "music.play";
    a1.version = 1;
    a1.name = "播放音乐";
    a1.description = "在指定设备播放音乐";

    ActionSummary a2 = new ActionSummary();
    a2.id = "music.pause";
    a2.version = 1;
    a2.name = "暂停";
    a2.description = "暂停播放";

    ActionsPayload ap = new ActionsPayload(Arrays.asList(a1, a2));

    client.sendActions(ap, new ActionsCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode ackPayload) {
        System.out.println("Actions ack: " + ackPayload.toPrettyString());
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Actions error: " + t.getMessage());
      }
    });

    RegisterPayload rp = new RegisterPayload();
    rp.plugin = new PluginInfo();
    rp.plugin.id = "com.ultrabar.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.ultrabar.music";

    // Example: wait for register completion using a CompletableFuture wrapper
    CompletableFuture<com.fasterxml.jackson.databind.JsonNode> regFut = new CompletableFuture<>();
    client.register(rp, new RegisterCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode registerResultPayload) {
        System.out.println("Register success: " + registerResultPayload.toPrettyString());
        regFut.complete(registerResultPayload);
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Register error: " + t.getMessage());
        regFut.completeExceptionally(t);
      }
    });

    // after register completes, do describe/call or other operations
    regFut.whenComplete((p, ex) -> {
      if (ex != null) {
        System.err.println("Register failed: " + ex.getMessage());
        return;
      }
      System.out.println("Ready to describe or call using saved session token in SDK");
    });

    // Setup CallHandler example (plugin receives incoming call requests from server)
    ExecutorService exec = Executors.newFixedThreadPool(4);
    client.setCallHandler((payload, responder) -> {
      Map<String,Object> params = payload.params;
      if (params == null || !params.containsKey("device")) {
        responder.sendError("MISSING_PARAM", "device is required", false, null);
        return;
      }
      String taskId = "task-" + java.util.UUID.randomUUID();
      responder.sendAccepted(null, taskId, "http://127.0.0.1:42101/tasks/" + taskId);
      exec.submit(() -> {
        try {
          Thread.sleep(1000);
          Map<String,Object> result = new HashMap<>();
          result.put("taskId", taskId);
          result.put("status", "ok");
          responder.sendSuccess(result);
        } catch (Exception e) {
          Map<String,Object> details = new HashMap<>();
          details.put("cause", e.getMessage());
          responder.sendError("EXEC_FAILED", "执行失败", true, details);
        }
      });
    });

    // keep running for demo
    Thread.sleep(30_000);
    client.stop();
    exec.shutdownNow();
  }
}
