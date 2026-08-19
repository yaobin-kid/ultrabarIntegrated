package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.*;

import java.util.Arrays;
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
        rp.plugin.name = "Music 测试";
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

            }

            @Override
            public void onCall(CallPayload payload, CallResponder responder) {
                // Incoming call: validate and process

            }
        });

        client.startAsync().thenRun(() -> System.out.println("Client started and will auto-register/send actions"));

        // Keep running for demo then shutdown
        Thread.sleep(30_000);
        client.stop();
        exec.shutdownNow();
    }
}
