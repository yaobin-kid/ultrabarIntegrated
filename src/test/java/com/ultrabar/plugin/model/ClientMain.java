package com.ultrabar.plugin.model;

import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.callback.PluginListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        PluginClient client = new PluginClient();

        RegisterPayload rp = new RegisterPayload();
        rp.name = "Music";
        rp.packageName = "com.ultrabar.music";
        client.setRegisterConfig(rp);

        ActionSummary a1 = new ActionSummary();
        a1.actionId = "music.play";
        a1.name = "play music";
        a1.description = "play on a device";

        ActionSummary a2 = new ActionSummary();
        a2.actionId = "music.pause";
        a2.name = "pause music";
        a2.description = "pause a device";
        client.setActionsConfig(new ActionsPayload(Arrays.asList(a1, a2)));

        ExecutorService exec = Executors.newFixedThreadPool(4);

        client.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload payload) {
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
            public void onActionsAck(ActionsResultPayload ack) {
                System.out.println("Actions result: success=" + ack.success + " received=" + ack.receivedCount);
            }

            @Override
            public void onActionsUpdate(ActionsPayload update) {
                int count = (update != null && update.actions != null) ? update.actions.size() : 0;
                System.out.println("Actions update pushed: count=" + count);
            }


            @Override
            public void onDescribe(DescribePayload payload, DescribeResponder responder) {
                System.out.println("接受到动作查询");
                DescribeResultPayload result = new DescribeResultPayload(true, null, null);
                result.actionId = payload.actionId;

                DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
                device.id = "deviceId";
                device.name = "device";
                device.required = true;
                device.type = ParameterType.SELECT;
                device.placeholder = "select a device";

                DescribeResultPayload.OptionSpec options = new DescribeResultPayload.OptionSpec();
                options.provider = OptionProvider.REMOTE;
                options.searchable = true;
                device.options = options;

                result.parameters = Arrays.asList(device);
                responder.sendSuccess(result);
            }

            @Override
            public void onCall(CallPayload payload, CallResponder responder) {
                Map<String, Object> data = new HashMap<String, Object>();
                data.put("actionId", payload.actionId);
                data.put("status", "ok");
                responder.sendSuccess(data);
            }

            @Override
            public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
                GetOptionsResultPayload result = new GetOptionsResultPayload();
                result.success = true;
                result.hasMore = false;
                result.nextCursor = null;

                Label item = new Label();
                item.value = "sp00-1";
                item.label = "sony tv";

                Label item2 = new Label();
                item2.value = "sp00-2";
                item2.label = "sony amp";

                result.items = Arrays.asList(item, item2);
                responder.sendSuccess(result);
            }
        });

        client.startAsync().thenRun(new Runnable() {
            @Override
            public void run() {
                System.out.println("Client started and will auto-register/send actions");
            }
        });

        Thread.sleep(30_000);
        client.stop();
        exec.shutdownNow();
    }
}
