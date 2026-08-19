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

    // Configure auto-register payload and auto-actions BEFORE start
    RegisterPayload rp = new RegisterPayload();
    rp.plugin = new PluginInfo();
    rp.plugin.id = "com.ultrabar.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.ultrabar.music";
    client.setAutoRegisterPayload(rp);

    ActionSummary a1 = new ActionSummary();
    a1.id = "music.play";
    a1.version = 1;
    a1.name = "播放音乐";
    a1.description = "在指定设备播放音乐";
    ActionsPayload ap = new ActionsPayload(Arrays.asList(a1));
    client.setAutoActionsPayload(ap);

    // Set CallHandler to process incoming calls from main App
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

    // Start the SDK. It will auto-register and auto-send actions once connected.
    client.startAsync().thenRun(() -> System.out.println("Connected and SDK started"));

    // Example of making an outbound describe/call after start
    Thread.sleep(2000);
    client.describe(null, null, "music.play").thenAccept(resp -> {
      System.out.println("Describe response: " + resp);
    }).exceptionally(ex -> { ex.printStackTrace(); return null; });

    // Run for demo then shutdown
    Thread.sleep(30_000);
    client.stop();
    exec.shutdownNow();
  }
}
