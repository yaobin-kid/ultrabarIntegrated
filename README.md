﻿﻿﻿# Ultrabar Plugin SDK

### [English](README.md) | [简体中文](README_zh.md) | [日本語](README_ja.md)

基于 Netty 的 Ultrabar 插件协议 SDK（协议 version = 2）。传输是 **一行一条 UTF-8 JSON**（`\n` 分帧）

| 角色          | 类                                  | 用途                                              |
| ----------- | ---------------------------------- | ----------------------------------------------- |
| 插件侧端        | `com.ultrabar.plugin.PluginClient` | 注册、上报 actions、处理 describe / get\_options / call |
| 主侧(LineOS)端 | `com.ultrabar.server.PluginServer` | 按 `packageName` 管理会话、保存动作、向插件发起 call            |

三方开发者属于`插件侧端角色`，只集成 **PluginClient** 按步骤操作即可。  ` LineOS 将定期扫描 AndroidManifest.xml 数据，获取服务并启动（已启动将跳过）`

## 1.gradle 引入

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

必要依赖：

```groovy
implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.0.12' //sdk ver

implementation "io.netty:netty-all:4.1.94.Final"
implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
implementation "org.slf4j:slf4j-simple:2.0.7"
```

```groovy
  packagingOptions {
        exclude 'META-INF/INDEX.LIST'
        exclude 'META-INF/io.netty.versions.properties'
    }
```

> **不要**使用 `netty-all`

## 2.服务

`AndroidManifest.xml `  ：

```xml
   <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <meta-data  android:name="ultrabar.plugin"   android:value="com.test.music" />
   </service>
```

### 必须的设置

> `meta-data` 属性 取值为 `applicationId`
> `android:exported="true"`
> android:permission="com.ultrabar.plugin.SERVER\_REGISER\_PERMISSION"

## 3.权限

`AndroidManifest.xml `  ：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 4. BackgroundService 服务参考代码

> 需在服务里完成 `PluginClient` 的启动工作 `start()` 部分。
>
> 声明的 `BackgroundService` 系统自动扫描并完成启动工作（以启则跳过）。 开发阶段为验证流程开发者可自启

```java

public class BackgroundService extends Service {
    public static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "BackgroundServiceChannel";

    public BackgroundService() {
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        AudioPlayerManager.getInstance().init(this);
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("常驻后台服务")
                .setContentText("正在运行...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();

        // 关键：启动后几秒内必须调用此方法，向系统“交差”
        startForeground(1001, notification);


        start();
    }

    public void start() {
        PluginClient c = new PluginClient();
        RegisterPayload rp = new RegisterPayload();
        rp.name = "TestMusic";
        rp.packageName = "com.test.music";


        ActionsPayload ap = new ActionsPayload();

        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "播放";
        play.description = "执行设备的播放";


        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "暂停";
        pause.description = "执行设备的暂停动作";


        ap.actions = Arrays.asList(play, pause);
        c.setRegisterConfig(rp);
        c.setActionsConfig(ap);

        c.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "注册端口号:" + result.configServer.port);
                //start http server
                RawHttpServer server = new RawHttpServer(result.configServer.port);
                server.startServer();
            }

            @Override
            public void onRegisterFailed(Throwable throwable) {

            }

            @Override
            public void onActionsFailed(Throwable throwable) {

            }

            @Override
            public void onActionsAck(ActionsResultPayload result) {
                Log.d(TAG, "动作注册成功了");
            }

            @Override

            public void onActionsUpdate(ActionsPayload actionsPayload) {

            }


            @Override
            public void onDescribe(DescribePayload describe, DescribeResponder describeResponder) {
                Log.d(TAG, "接受到数据查询方法:" + describe.actionId);
                DescribeResultPayload resultPayload = new DescribeResultPayload();
                resultPayload.actionId = describe.actionId;
                resultPayload.success = true;
                resultPayload.parameters = new ArrayList<>();


                DescribeResultPayload.ParameterSpec spec1 = new DescribeResultPayload.ParameterSpec();
                spec1.id = "deviceId";
                spec1.name = "设备";
                spec1.placeholder = "请选择设备";
                spec1.required = true;
                spec1.type = ParameterType.SELECT;
                spec1.options = new DescribeResultPayload.OptionSpec();
                spec1.options.searchable = false;
                spec1.options.provider = OptionProvider.STATIC;
                spec1.options.items = new ArrayList<>();
                spec1.options.items.add(new Label("客厅播放器", "test01"));
                spec1.options.items.add(new Label("卧室播放器", "test02"));


                DescribeResultPayload.ParameterSpec spec2 = new DescribeResultPayload.ParameterSpec();
                spec2.id = "title";
                spec2.name = "歌曲名称";
                spec2.placeholder = "输入歌曲名称";
                spec2.required = true;
                spec2.type = ParameterType.TEXT;
                spec2.options = new DescribeResultPayload.OptionSpec();
                spec2.options.searchable = false;
                spec2.options.provider = OptionProvider.STATIC;


                DescribeResultPayload.ParameterSpec spec3 = new DescribeResultPayload.ParameterSpec();
                spec3.id = "in";
                spec3.name = "设备";
                spec3.placeholder = "输入源";
                spec3.required = true;
                spec3.type = ParameterType.SELECT;
                spec3.options = new DescribeResultPayload.OptionSpec();
                spec3.options.searchable = false;
                spec3.options.provider = OptionProvider.REMOTE;
                spec3.dependsOn = new ArrayList<>();
                spec3.dependsOn.add("deviceId");

                if ("music.play".equals(describe.actionId)) {
                    resultPayload.parameters.add(spec1);
                    resultPayload.parameters.add(spec2);
                    resultPayload.parameters.add(spec3);
                }

                describeResponder.sendSuccess(resultPayload);
            }

            @Override
            public void onCall(CallPayload call, CallResponder callResponder) {
                if ("music.play".equals(call.actionId)) {
                    AudioPlayerManager.getInstance().play();
                    Intent intent = new Intent(MyApp.context, TestActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 必须！
                    startActivity(intent);
                } else if ("music.pause".equals(call.actionId)) {
                    AudioPlayerManager.getInstance().pause();
                }
                callResponder.sendSuccess(null);
            }

            @Override
            public void onOptions(GetOptionsPayload request, OptionsResponder optionsResponder) {
                if ("music.play".equals(request.actionId)) {
                    if ("in".equals(request.describeId)) {
                        GetOptionsResultPayload result = new GetOptionsResultPayload();
                        result.hasMore = false;
                        result.nextCursor = "0";
                        result.items = new ArrayList<>();
                        result.items.add(new Label("qq音乐", "qq"));
                        result.items.add(new Label("网易音乐", "163"));
                        optionsResponder.sendSuccess(result);
                    }
                }
            }
        });

        c.startAsync();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {


        return null;
    }

}
```

## PluginClient 详细说明

```java
    PluginClient client = new PluginClient();
    //配置注册信息
    RegisterPayload rp = new RegisterPayload();
    rp.name = "Music"; //应用名称
    rp.packageName = "com.ultrabar.music"; //建议包名


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

```

动作列表变化时调用 `client.updateActions(new ActionsPayload(...))`。

## PluginServer（主 App）

会话按注册时的 `packageName` 唯一；同一 `packageName` 再次注册会顶掉旧连接。只有注册回调返回 `success == true` 才会创建 `PluginSession`。

```java
final PluginServer server = new PluginServer(39001);

server.setRegisterHandler(new PluginRegisterHandler() {
    @Override
    public RegisterResultPayload handleRegister(RegisterPayload request) {
        RegisterResultPayload result = new RegisterResultPayload();
        if (request.packageName == null) {
            result.success = false;
            result.error = ErrorInfo.of("REJECTED", "packageName required", false, null);
            return result;
        }
        result.success = true;
        result.configServer = new ConfigServer();
        result.configServer.host = "127.0.0.1";
        result.configServer.port = 8123;
        // sessionId / sessionToken / heartbeat 不填时服务端会补默认值
        return result;
    }
});

server.setListener(new PluginServerListener() {
    @Override
    public void onRegistered(PluginSession session) { }

    @Override
    public void onActionsUpdated(PluginSession session) { }

    @Override
    public void onUnregistered(PluginSession session) { }
});

server.start();

// 查询会话
PluginSession session = server.getSession("com.ultrabar.music");

// 调用插件动作（需该会话已上报对应 actionId）
Map<String, Object> params = new HashMap<String, Object>();
params.put("deviceId", "sp00-1");
server.call("music.play", params)
        .thenAccept(result -> { /* CallResultPayload */ })
        .exceptionally(err -> { err.printStackTrace(); return null; });

// 多个插件可能有同名 actionId 时带上 packageName
server.call("com.ultrabar.music", "music.play", params);

// server.stop();
```

## A2UI 功能（A2UI v0.9 协议）

A2UI 基于 **[A2UI v0.9 协议](https://a2ui.org/specification/v0.9-a2ui/)**，是插件（Agent）↔ 主 App（Renderer）之间的生成式 UI 通道：

- **插件 → 主 App**：上报 A2UI envelope 消息（创建 surface、更新组件树、更新数据模型），主 App 缓存到 `PluginSession` 并通过 `PluginServerListener` 回调通知
- **主 App → 插件**：用户在 UI 上的交互以 action 形式回传给插件处理

> ⚠️ **协议版本**：本功能传输的所有 JSON 均为 **A2UI v0.9** 格式，每条 envelope 必须带 `"version": "v0.9"` 字段。**不要**使用 v0.8 的 `beginRendering` / `surfaceUpdate` / `dataModelUpdate` 消息名。

### A2UI v0.9 消息类型对照

| Envelope key       | 方向      | 对应 API                      | 用途            |
| ------------------ | ------- | --------------------------- | ------------- |
| `createSurface`    | 插件→主App | `refreshA2UI`            | 创建 UI surface |
| `updateComponents` | 插件→主App | `refreshA2UI`            | 定义/更新组件树      |
| `updateDataModel`  | 插件→主App | `sendA2UIUpdateDataModel` | 写入/更新数据模型     |
| `deleteSurface`    | 插件→主App | `refreshA2UI`            | 删除 surface    |
| `action`           | 主App→插件 | `callA2UIAction`          | 用户交互回传        |

### 1. 启用 A2UI

插件注册时声明 `supportsA2UI = true`（**必须**，否则主 App 调用会被服务端拒绝）：

```java
RegisterPayload reg = new RegisterPayload();
reg.packageName = "com.example.plugin";
reg.name = "示例插件";
reg.versionCode = 1;
reg.versionName = "1.0.0";
reg.protocolVersion = 1;
reg.supportsA2UI = true;   // ★ 关键开关
// ... 其他字段
```

### 2. 插件端：刷新已存在视图（refreshA2UI）

`refreshA2UI(A2UIRefreshItem item)` 发送**完整的 A2UI v0.9 envelope**，用于刷新已存在视图的 `createSurface` / `updateComponents` / `deleteSurface`。每个 `A2UIRefreshItem` 包含 `instanceId`（实例 id）、`layoutId`（布局 id）和 `ui`（A2UI 消息列表，JSON 数组；构造参数传 JSON 字符串时 SDK 内部自动解析）。

> 另有 `refreshA2UI(List<A2UIRefreshItem> items)` 重载可批量刷新多个视图。
> 卡片首次定义由服务端 `queryA2UIData` 拉取，插件在 `onA2UIDataRequest` 返回完整 `A2UIItem`（layoutId / name / cardId / ui / args）。

**2.1 创建视图 伪代码**：

```Java
String ui = {  
            "name": "Focus Card",
            "description": "带按钮交互与数据绑定的任务卡片。",
            "messages": [
               {
                  "createSurface": {
                     "catalogId": "https://a2ui.org/specification/v0_9/standard_catalog.json",
                     "sendDataModel": true,
                     "surfaceId": "example-focus"
                  },
                  "version": "v0.9"
               },
               {
                  "version": "v0.9",
                  "updateComponents": {
                     "components": [
                        {
                           "component": "Card",
                           "styles": {
                              "border-radius": "120px",
                              "filter": "drop-shadow(0px 6px 24px 0px rgba(0, 0, 0, 0.08))",
                              "padding": "0px",
                              "width": "100%",
                              "height": "100%"
                           },
                           "id": "root",
                           "child": "card-stack"
                        },
                        {
                           "component": "Column",
                           "children": [
                              "bg-image",
                              "content"
                           ],
                           "styles": {
                              "border-radius": "60px",
                              "overflow": "hidden",
                              "width": "100%",
                              "position": "relative",
                              "height": "100%"
                           },
                           "id": "card-stack"
                        },
                        {
                           "fit": "cover",
                           "component": "Image",
                           "styles": {
                              "top": "0px",
                              "left": "0px",
                              "width": "100%",
                              "position": "absolute",
                              "height": "100%"
                           },
                           "id": "bg-image",
                           "url": "https://sslcdn.lifex360.com/icon/2026/07/7abvzteswetomhnej2eq.png"
                        },
                        {
                           "component": "Column",
                           "children": [
                              "eyebrow",
                              "headline",
                              "body",
                              "divider",
                              "footer"
                           ],
                           "styles": {
                              "padding": "24px",
                              "z-index": "1",
                              "gap": "14px",
                              "position": "relative"
                           },
                           "id": "content",
                           "align": "stretch"
                        },
                        {
                           "component": "Text",
                           "styles": {
                              "margin-left": "20px",
                              "color": "#FFFFFF",
                              "font-weight": "bold",
                              "font-size": "38px",
                              "line-height": "38px"
                           },
                           "id": "eyebrow",
                           "text": {
                              "path": "/eyebrow"
                           }
                        },
                        {
                           "component": "Text",
                           "styles": {
                              "color": "#FFFFFF",
                              "font-weight": "bold",
                              "font-size": "38px",
                              "line-height": "48px"
                           },
                           "id": "headline",
                           "text": {
                              "path": "/headline"
                           }
                        },
                        {
                           "component": "Text",
                           "styles": {
                              "color": "#FFFFFF",
                              "font-size": "26px",
                              "line-height": "38px"
                           },
                           "id": "body",
                           "text": {
                              "path": "/body"
                           }
                        },
                        {
                           "component": "Divider",
                           "styles": {
                              "color": "rgba(255, 255, 255, 0.3)",
                              "height": "1px"
                           },
                           "id": "divider"
                        },
                        {
                           "component": "Row",
                           "children": [
                              "progress",
                              "button"
                           ],
                           "justify": "spaceBetween",
                           "id": "footer",
                           "align": "center"
                        },
                        {
                           "component": "Text",
                           "styles": {
                              "color": "#FFFFFF",
                              "font-size": "26px",
                              "line-height": "34px"
                           },
                           "id": "progress",
                           "text": {
                              "path": "/progress"
                           }
                        },
                        {
                           "component": "Button",
                           "action": {
                              "functionCall": {
                                 "call": "handleWidgetAction",
                                 "args": {
                                    "value": "打卡成功！"
                                 }
                              }
                           },
                           "styles": {
                              "border-radius": "12px",
                              "background-color": "#1A73E8",
                              "padding": "12px 24px",
                              "width": "220px",
                              ":active": {
                                 "background-color": "#1557B0",
                                 "transform": "scale(0.97)",
                                 "opacity": "0.85"
                              },
                              "height": "64px"
                           },
                           "id": "button",
                           "child": "button-label"
                        },
                        {
                           "component": "Text",
                           "styles": {
                              "color": "#FFFFFF",
                              "font-weight": "bold",
                              "font-size": "26px",
                              "line-height": "34px",
                              "text-align": "center"
                           },
                           "id": "button-label",
                           "text": {
                              "path": "/buttonLabel"
                           }
                        }
                     ],
                     "surfaceId": "example-focus"
                  }
               },
               {
                  "updateDataModel": {
                     "surfaceId": "example-focus",
                     "value": {
                        "eyebrow": "今日重点Change",
                        "progress": "1 / 3",
                        "body": "通过 RecyclerView + A2UI，把服务端下发的 JSON 直接渲染成原生卡片。",
                        "headline": "完成 A2UI 接入",
                        "buttonLabel": "打卡"
                     }
                  },
                  "version": "v0.9"
               }
            ]
         }
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", ui));
```

 建议在 onRegisterSuccess 回调里调用  refreshA2UI 上报当前ui状态

**2.2 更新视图状态值 伪代码**：

```Java
String update = {   
            "version": "v0.9",
            "updateDataModel": {
                "surfaceId": "example-focus",
                "value": {
                    "eyebrow": "今日重点",
                    "headline": "完成 A2UI 接入",
                    "body": "通过 RecyclerView + A2UI，把服务端下发的 JSON 直接渲染成原生卡片。",
                    "progress": "1 / 3",
                    "buttonLabel": "打PC卡"
                }
            }
 }

 client.sendA2UIUpdateDataModel("example-focus", update);

```

<br />

**2.3 事件回传 参考代码**

```Java
{
                           "component": "Button",
                           "action": {
                              "functionCall": {
                                 "call": "handleWidgetAction",
                                 "args": {
                                    "value": "打卡成功！"
                                 }
                              }
                           },
                          
                           "id": "button",
                           "child": "button-label"
                        },

 functionCall 对象里的call属性取值 handleWidgetAction  点击时会把args 参数透传到 PluginListener.onA2UIActionCall 回调
```

<br />

**2.4 服务端主动拉取数据（queryA2UIData）**

除了插件主动 `refreshA2UI` 推送，主 App 也可以主动拉取插件当前的 a2ui 数据：

```Java
// 主 App 端：发起拉取
server.queryA2UIData("com.example.plugin").thenAccept(result -> {
    if (result.success) {
        for (A2UIItem item : result.items) {
            // item.layoutId / item.ui
        }
    }
});
```

```Java
// 插件端：实现回调返回当前数据
client.setPluginListener(new PluginListener() {
    @Override
    public List<A2UIItem> onA2UIDataRequest() {
        List<A2UIItem> items = new ArrayList<>();
        items.add(new A2UIItem("example-focus", currentUiJson));
        return items;   // 返回 null 或空列表表示无数据
    }
});
```

拉取成功后，服务端会自动将数据缓存到 `PluginSession`，调用方从返回值的 `items` 取数据。

<br />

<br />

### DOT\_KEY 模块注册 参考代码

```java
PluginClient client = new PluginClient();
//注册模块 可变参数 传入需要的模块数据
client.subscribeModuleEvents(ModuleType.DOT_KEY,ModuleType.KNOB_KEY);
client.setPluginListener(new PluginListener(){

    /**
     * 模块事件回调函数
     */
    @Override
    public void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
        //在前台就消费该事件
        if (ModuleEventPayload.APP_STATE_FOREGROUND.equals(payload.appState)) {
            responder.consume(); //消费事件
        }else{
            responder.ignore(null); //忽略该事件
        }
        
        Log.w(TAG, String.format("onModuleEvent 模块事件:%s", JSON.toJSON(payload)));
    }
    
});
```

#### DOT KEY 模块 payload 数据格式

```json
{
    "appState": "background", // none 无状态， background 当前程序在后台， foreground 程序在前天
    "module": "DOT_KEY",  //模块类型
    "action": "button",
    "event": {   //按键原始消息
        "action": 1,    //1 down ，0 up
        "downTime": 169360339,
        "eventTime": 169360339,
        "keyCode": 2,  //按键码， 取值 0、1、2
        "repeatCount": 0  //长按+1
    },
    "moduleId": "4",  //模块id
    "sn": "xxxxx",  //模块串号
    "timestamp": 1793002523558
}
```

#### KNOB KEY 模块payload 数据

```json
//button 按键事件
{
  "appState": "foreground",
  "module": "KNOB_KEY",
  "action": "button",
  "event": {
    "action": 1,
    "downTime": 171968479,
    "eventTime": 171968479,
    "keyCode": 0,
    "repeatCount": 0
  },
  "moduleId": "6",
  "sn": "xxx",  //模块串号
  "timestamp": 1793005131698
}


//旋钮事件 （转得越快报得越快）
{
  "appState": "foreground",
  "module": "KNOB_KEY",
  "action": "rotate",
  "event": {
    "value": 1   // 1 顺时针，-1 逆时针
  },
  "moduleId": "6",
  "sn": "xxxx",  //模块串号
  "timestamp": 1793005179031
}

```

> 1.客户端调用 subscribeModuleEvents函数注册对于的模块事件
> 2.客户端调用setPluginListener 注册回调并实现PluginListener 接口里的 onModuleEvent 方法。
> 当模块触发对应功能时就回调该方法。如果消费该事件则调用 responder.consume(); responder.ignore(null) 忽略 或者超时LineOS 走兜底流程触发 模块绑定的profile 配置功能。


# 查询所有模块
```java

PluginClient c = new PluginClient();
//查询所有
CompletableFuture<QueryModulesResultPayload> f = c.queryModules();
//查询在线模块
// CompletableFuture<QueryModulesResultPayload> f = c.queryModules(ModuleOnlineFilter.ONLINE);
//查询离线模块
//CompletableFuture<QueryModulesResultPayload> f = c.queryModules(ModuleOnlineFilter.OFFLINE);
f.thenAccept(c -> {
  //打印queryModules查询结果:{"modules":[{"module":"SENSE_CUBE","moduleId":"31","name":"Sense Cube","online":false,"sn":"0000019F20E45C31"},{"module":"DOT_KEY","moduleId":"37","name":"Dot Key","online":true,"sn":"0000019E4F84ABEF"}],"success":true}
  Log.d(TAG, "queryModules查询结果:" + JSONObject.toJSONString(c));
});

```
> 目前仅 返回  DOT_KEY, KNOB_KEY, SENSE_CUBE;

#### QueryModulesResultPayload 数据结构
```json
{
    "modules": [
        {
            "module": "SENSE_CUBE",   //模块 仅支持  DOT_KEY, KNOB_KEY, SENSE_CUBE;
            "moduleId": "31",  //模块id
            "name": "Sense Cube", //名称
            "online": false, //在线状态 true 在线，false  离线
            "sn": "0000019F20E45C31"  //设备串号
        } 
    ],
    "success": true
}
```