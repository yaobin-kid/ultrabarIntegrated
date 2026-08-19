package com.example.plugin;

import com.example.plugin.callback.ActionsCallback;
import com.example.plugin.callback.ActionsUpdateCallback;
import com.example.plugin.callback.RegisterCallback;
import com.example.plugin.model.ActionsPayload;
import com.example.plugin.model.ActionSummary;
import com.example.plugin.model.RegisterPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ExampleMain {
  public static void main(String[] args) throws Exception {
    PluginClient client = new PluginClient("127.0.0.1", 39001);
    client.start();

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
    rp.plugin = new com.example.plugin.model.PluginInfo();
    rp.plugin.id = "com.example.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.example.music";

    client.register(rp, new RegisterCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode registerResultPayload) {
        System.out.println("Register success: " + registerResultPayload.toPrettyString());
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Register error: " + t.getMessage());
      }
    });

    // describe example
    CompletableFuture<Object> desc = client.describe(null, null, "music.play");
    desc.whenComplete((p, ex) -> {
      if (ex != null) System.err.println("Describe failed: " + ex.getMessage());
      else System.out.println("Describe: " + p.toString());
    });

    // call example
    Map<String, Object> params = new HashMap<>();
    params.put("device", "speaker-001");
    params.put("keyword", "晴天");
    params.put("mode", "normal");

    CompletableFuture<Object> callF = client.call(null, null, "music.play", params);
    callF.whenComplete((p, ex) -> {
      if (ex != null) System.err.println("Call failed: " + ex.getMessage());
      else System.out.println("Call result: " + p.toString());
    });

    Thread.sleep(15000);
    client.stop();
  }
}
