package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.PluginInfo;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.DescribeResultPayload;
import com.ultrabar.plugin.model.ActionsAckPayload;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.CallPayload;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExampleMain {
  public static void main(String[] args) throws Exception {
    PluginClient client = new PluginClient("127.0.0.1", 39001);

    // Configure register/actions BEFORE start
    RegisterPayload rp = new RegisterPayload();
    rp.plugin = new PluginInfo();
    rp.plugin.id = "com.ultrabar.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.ultrabar.music";
    client.setRegisterConfig(rp);

    ActionSummary a1 = new ActionSummary();
    a1.id = "music.play";
    a1.version = 1;
    a1.name = "播放音乐";
    a1.description = "在指定设备播放音乐";
    ActionsPayload ap = new ActionsPayload(Arrays.asList(a1));
    client.setActionsConfig(ap);

    // Executor for handling incoming calls / describe processing
    ExecutorService exec = Executors.newFixedThreadPool(4);
    AtomicBoolean httpStarted = new AtomicBoolean(false);

    client.setPluginListener(new PluginListener() {
      @Override
      public void onRegisterSuccess(com.ultrabar.plugin.model.RegisterResultPayload payload) {
        System.out.println("Register success: session=" + payload.sessionId);
        // Start a simple HTTP server once, using configServer if provided
        if (httpStarted.compareAndSet(false, true)) {
          String host = "0.0.0.0";
          int port = 42101;
          if (payload.configServer != null) {
            if (payload.configServer.host != null) host = payload.configServer.host;
            if (payload.configServer.port != null) port = payload.configServer.port;
          }
          final String bindHost = host;
          final int bindPort = port;
          new Thread(() -> {
            try {
              com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                  new java.net.InetSocketAddress(bindHost, bindPort), 0);

              server.createContext("/health", exchange -> {
                String resp = "ok";
                exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(200, resp.getBytes().length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                  os.write(resp.getBytes());
                }
              });

              System.out.println("Starting HTTP server on " + bindHost + ":" + bindPort + " (session=" + payload.sessionId + ")");
              server.start();
            } catch (Exception e) {
              e.printStackTrace();
              httpStarted.set(false);
            }
          }, "plugin-http-starter").start();
        }
      }

      @Override
      public void onRegisterFailed(Throwable t) {
        System.err.println("Register failed: " + t.getMessage());
      }

      @Override
      public void onActionsFailed(Throwable t) {
        System.err.println("Actions failed: " + t.getMessage());
      }

      @Override
      public void onActionsAck(ActionsAckPayload ack) {
        System.out.println("Actions ack: success=" + ack.success + " received=" + ack.receivedCount);
      }

      @Override
      public void onActionsUpdate(ActionsPayload update) {
        System.out.println("Actions update pushed: count=" + (update != null && update.actions != null ? update.actions.size() : 0));
      }

      @Override
      public void onDescribeSuccess(DescribeResultPayload result) {
        System.out.println("Outbound describe completed: success=" + result.success);
      }

      @Override
      public void onDescribeError(Throwable t) {
        System.err.println("Outbound describe error: " + t.getMessage());
      }

      @Override
      public void onDescribe(DescribePayload payload, DescribeResponder responder) {
        // Incoming describe from server: process and respond
        exec.submit(() -> {
          try {
            DescribeResultPayload dr = new DescribeResultPayload();
            dr.success = true;
            Map<String, Object> details = new HashMap<>();
            details.put("exampleParam", "value");
            dr.details = details;
            responder.sendSuccess(dr);
          } catch (Exception e) {
            responder.sendError("DESCRIBE_FAILED", e.getMessage(), false, null);
          }
        });
      }

      @Override
      public void onCall(CallPayload payload, CallResponder responder) {
        // Incoming call: validate and process
        exec.submit(() -> {
          try {
            Map<String, Object> params = payload.params;
            if (params == null || !params.containsKey("device")) {
              responder.sendError("MISSING_PARAM", "device is required", false, null);
              return;
            }
            String taskId = "task-" + java.util.UUID.randomUUID();
            responder.sendAccepted(null, taskId, "http://127.0.0.1:42101/tasks/" + taskId);

            // Simulate work
            Thread.sleep(1000);
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", "ok");
            responder.sendSuccess(result);
          } catch (Exception e) {
            Map<String, Object> details = new HashMap<>();
            details.put("cause", e.getMessage());
            responder.sendError("EXEC_FAILED", "执行失败", true, details);
          }
        });
      }
    });

    client.startAsync().thenRun(() -> System.out.println("Client started and will auto-register/send actions"));

    // Keep running for demo then shutdown
    Thread.sleep(30_000);
    client.stop();
    exec.shutdownNow();
  }
}
