package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.*;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleMain {
    public static void main(String[] args) throws Exception {
        PluginClient client = new PluginClient();

        // Configure register/actions BEFORE start
        RegisterPayload rp = new RegisterPayload();
        rp.id = "com.ultrabar.music";
        rp.name = "Music 测试";
        rp.version = "1.2.0";
        rp.packageName = "com.ultrabar.music";
        client.setRegisterConfig(rp);

        ActionSummary a1 = new ActionSummary();
        a1.id = "music.play";
        a1.name = "播放音乐";
        a1.description = "在指定设备播放音乐";


        ActionSummary a2 = new ActionSummary();
        a2.id = "music.pause";
        a2.name = "暂停音乐";
        a2.description = "暂停指定设备";
        ActionsPayload ap = new ActionsPayload(Arrays.asList(a1, a2));

        client.setActionsConfig(ap);

        // Executor for handling incoming calls / describe processing
        ExecutorService exec = Executors.newFixedThreadPool(4);

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
                System.out.println("onDescribe........");
                DescribeResultPayload describeResultPayload = new DescribeResultPayload(true, null, null);
                describeResultPayload.actionId = payload.actionId;


                DescribeResultPayload.Parameters p1 = new DescribeResultPayload.Parameters();
                p1.id = "deviceId";
                p1.name = "播放设备";
                p1.required = true;
                p1.type = "select";
                p1.placeholder = "请选择播放设备";

                DescribeResultPayload.Options op = new DescribeResultPayload.Options();
                op.provider = "remote";
                op.searchable = true;
                p1.options = op;

                describeResultPayload.parameters = Arrays.asList(p1);

                responder.sendSuccess(describeResultPayload);

            }

            @Override
            public void onCall(CallPayload payload, CallResponder responder) {
                // Incoming call: validate and process

                System.out.println("onCall........");

            }

            @Override
            public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
                System.out.println("onOptions........");
                OptionsResult result = new OptionsResult();
                result.success = true;
                result.hashMore=false;
                result.nextCursor  = null;
                Item item = new Item();
                item.value = "sp00-1";
                item.label = "客厅sony电视";


                Item item2 = new Item();
                item2.value = "sp00-1";
                item2.label = "客厅sony功放";


                result.items = Arrays.asList(item,item2);

                responder.sendSuccess(result);
            }
        });

        client.startAsync().thenRun(() -> System.out.println("Client started and will auto-register/send actions"));

        // Keep running for demo then shutdown
        Thread.sleep(30_000);
        client.stop();
        exec.shutdownNow();
    }
}
