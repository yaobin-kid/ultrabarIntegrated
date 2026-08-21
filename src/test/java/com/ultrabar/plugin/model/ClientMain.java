package com.ultrabar.plugin.model;

import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.CallResponder;
import com.ultrabar.plugin.callback.DescribeResponder;
import com.ultrabar.plugin.callback.OptionsResponder;
import com.ultrabar.plugin.callback.PluginListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientMain {
    public static void main(String[] args) throws Exception {


        PluginClient client = new PluginClient();
        //配置注册信息
        RegisterPayload rp = new RegisterPayload();
        rp.name = "Music";
        rp.packageName = "com.ultrabar.music";


        //支持的动作
        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "play music";
        play.description = "play on a device";

        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "pause music";
        pause.description = "pause a device";


        ActionSummary stop = new ActionSummary();
        stop.actionId = "music.stop";
        stop.name = "stop all play";
        stop.description = "stop all device  play";


        client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause, stop)));
        client.setRegisterConfig(rp);


        ExecutorService exec = Executors.newFixedThreadPool(4);

        client.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload payload) {
                // ============================================================
                // 注册成功
                //
                // SDK 与主 App 完成注册后，会通过 payload 返回当前会话信息。
                //
                // configServer.port：
                //     主 App 分配给当前插件的 HTTP 调试服务端口。
                //
                // 第三方 App 需要在该端口启动自己的 HTTP Server，
                // 用于提供插件调试页面。
                //
                // 当主 App 打开插件调试页面时，会携带 sessionToken。
                //
                // sessionToken：
                //     当前插件调试会话的身份凭证。
                //
                // 注意：
                //     sessionToken 仅负责传递给第三方 App，
                //     第三方 App 必须自行校验 Token 的合法性，
                //     不要默认认为收到的 Token 一定可信。
                //
                // HTTP Server 建议在这里启动，并绑定到 payload.configServer.port。
                // ============================================================


                int httpPort = payload.configServer.port;
                String sessionToken = payload.sessionToken;

                System.out.println("Register success: session=" + payload.sessionId+",port="+httpPort+",sessionToken=+sessionToken");
            }

            @Override
            public void onRegisterFailed(Throwable t) {
                // 注册失败。
                //
                // 注册失败后表示当前插件暂时无法与主 App 正常通信，
                // 此时不要启动依赖主 App 的 HTTP / Action 服务。
                //
                // 可以根据实际业务进行：
                // 1. 记录错误日志
                // 2. 提示用户
                // 3. 等待 SDK 自动重连
                // 4. 必要时重新初始化 Plugin Client

                System.err.println("Register failed: " + t.getMessage());
            }

            @Override
            public void onActionsFailed(Throwable t) {
                System.err.println("Actions failed: " + t.getMessage());
            }

            @Override
            public void onActionsAck(ActionsResultPayload ack) {
                // ============================================================
                // Action 配置处理结果
                //
                // 当第三方 App 向主 App 发送 Action 配置后，
                // 主 App 会返回处理结果。
                //
                // success：
                //     主 App 是否成功接收/处理
                //
                // receivedCount：
                //     主 App 实际接收到的 Action 数量
                //
                // 注意：
                //     这是“Action 配置同步”的 ACK，
                //     不是 onCall() 的 Action 执行结果。
                // ============================================================

                System.out.println("Actions result: success=" + ack.success + " received=" + ack.receivedCount);
            }

            @Override
            public void onActionsUpdate(ActionsPayload update) {
                int count = (update != null && update.actions != null) ? update.actions.size() : 0;
                System.out.println("Actions update pushed: count=" + count);
            }


            @Override
            public void onDescribe(DescribePayload payload, DescribeResponder responder) {

                // ============================================================
                // Action 参数描述
                //
                // 主 App 在调用某个 Action 之前，会先通过 actionId 获取该 Action
                // 的参数定义。
                //
                // 这里需要告诉主 App：
                //   1. 这个 Action 需要哪些参数
                //   2. 参数的 id / 名称 / 类型
                //   3. 参数是否必填
                //   4. 如果参数需要选择，选项由哪里提供
                //
                // 参数定义中的 id 非常重要：
                // 后续 onCall() 收到的 payload.params 会使用这里定义的 id
                // 作为参数 Key。
                // ============================================================


                DescribeResultPayload result = new DescribeResultPayload();
                result.actionId = payload.actionId;
                result.success = true;
                if ("music.play".equals(payload.actionId)) { //获取播放动作的参数
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
                } else if ("music.pause".equals(payload.actionId)) { //pause 静态参数测试
                    DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
                    device.id = "deviceId2";
                    device.name = "device";
                    device.required = true;
                    device.type = ParameterType.SELECT;
                    device.placeholder = "select a device";
                    DescribeResultPayload.OptionSpec options = new DescribeResultPayload.OptionSpec();
                    options.provider = OptionProvider.STATIC;
                    options.searchable = true;
                    options.items = new ArrayList<>();
                    options.items.add(new Label("pause test 01", "pause_test_01"));
                    options.items.add(new Label("pause test 02", "pause_test_02"));
                    device.options = options;
                    result.parameters = Arrays.asList(device);
                }


                responder.sendSuccess(result);
            }


            @Override
            public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
                // ============================================================
                // 动态参数选项
                //
                // 当 onDescribe() 中将参数的 OptionProvider 设置为 REMOTE 后，
                // 主 App 会在需要展示该参数选项时调用这里。
                //
                // payload.actionId  -> 当前 Action
                // payload.describeId -> 当前参数的 id
                //
                // 例如：
                //   actionId  = music.play
                //   describeId = deviceId
                //
                // 表示主 App 正在请求：
                //   "请告诉我 music.play 的 deviceId 参数有哪些可选设备"
                //
                // 如果选项数量较多，可以通过 hasMore / nextCursor 实现分页。
                // ============================================================

                GetOptionsResultPayload result = new GetOptionsResultPayload();
                result.success = true;
                result.hasMore = false;
                result.nextCursor = null;

                if ("music.play".equals(payload.actionId)) { //动作id

                    if ("deviceId".equals(payload.describeId)) { //参数id
                        Label item = new Label();
                        item.value = "sp00-1";
                        item.label = "sony tv";
                        Label item2 = new Label();
                        item2.value = "sp00-2";
                        item2.label = "sony amp";
                        result.items = Arrays.asList(item, item2);
                    }
                }


                responder.sendSuccess(result);
            }


            @Override
            public void onCall(CallPayload payload, CallResponder responder) {

                // ============================================================
                // Action 执行, 解析出动作并执行最终返回 数据
                //
                // 当用户在主 App 中配置好 Action 并执行后，
                // 主 App 会调用这里。
                //
                // payload.actionId
                //     -> 要执行的 Action
                //
                // payload.params
                //     -> 用户在 onDescribe() 定义的参数
                //
                // 注意：
                // params 的 Key 必须与 onDescribe() 中
                // ParameterSpec.id 保持一致。
                //
                // 例如 onDescribe()：
                //
                //     device.id = "deviceId";
                //
                // 那么这里必须通过：
                //
                //     params.get("deviceId");
                //
                // 获取用户选择的设备。
                // ============================================================


                String actionId = payload.actionId;
                Map<String, Object> params = payload.params;
                if ("music.play".equals(actionId)) {
                    // onDescribe DescribeResultPayload.ParameterSpec.id
                    String deviceId = (String) params.get("deviceId");
                    //todo 完成指定设备的播放操作


                } else if ("music.pause".equals(actionId)) {
                    //onDescribe DescribeResultPayload.ParameterSpec.id
                    String deviceId2 = (String) params.get("deviceId2");
                    //todo 完成指定设备的暂停操作

                } else if ("music.stop".equals(actionId)) {
                    //todo 停止所有设备
                }

                Map<String, Object> data = new HashMap<String, Object>();
                data.put("actionId", payload.actionId);
                data.put("status", "ok");


                responder.sendSuccess(data);
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
