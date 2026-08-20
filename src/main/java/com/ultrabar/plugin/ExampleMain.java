package com.ultrabar.plugin;

import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionsResultPayload;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.model.DescribePayload;
import com.ultrabar.plugin.model.DescribeResultPayload;
import com.ultrabar.plugin.model.GetOptionsPayload;
import com.ultrabar.plugin.model.GetOptionsResultPayload;
import com.ultrabar.plugin.model.Item;
import com.ultrabar.plugin.model.OptionProvider;
import com.ultrabar.plugin.model.ParameterType;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.RegisterResultPayload;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleMain {
    public static void main(String[] args) throws Exception {
        PluginClient client = new PluginClient();

        RegisterPayload rp = new RegisterPayload();
        rp.id = "com.ultrabar.music";
        rp.name = "Music";
        rp.version = "1.2.0";
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
            public void onDescribeSuccess(DescribeResultPayload result) {
                System.out.println("Outbound describe completed: success=" + result.success);
            }

            @Override
            public void onDescribeError(Throwable t) {
                System.err.println("Outbound describe error: " + t.getMessage());
            }

            @Override
            public void onDescribe(DescribePayload payload, DescribeResponder responder) {
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

                Item item = new Item();
                item.value = "sp00-1";
                item.label = "sony tv";

                Item item2 = new Item();
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
