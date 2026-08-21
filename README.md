# Ultrabar Plugin SDK

基于 Netty 的 Ultrabar 插件协议 SDK（协议 version = 2）。传输是 **一行一条 UTF-8 JSON**（`\n` 分帧），默认地址 `127.0.0.1:39001`。

| 角色 | 类 | 用途 |
|---|---|---|
| 插件侧 | `com.ultrabar.plugin.PluginClient` | 注册、上报 actions、处理 describe / get_options / call |
| 主 App 侧 | `com.ultrabar.server.PluginServer` | 按 `packageName` 管理会话、保存动作、向插件发起 call |

Android 上一般只集成 **PluginClient**，连到 PC / 主 App 上的 PluginServer。

## 源码构建

```bash
git clone https://github.com/yaobin-kid/ultrabarIntegrated.git
cd ultrabarIntegrated
./gradlew jar
```

产物：`build/libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar`（**不含** Netty / Jackson / slf4j，宿主项目需要自行声明依赖）。


## android studio 引入
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```
```groovy
implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.0.2'
```

本仓库自带的依赖版本：

```groovy
implementation "io.netty:netty-all:4.1.94.Final"
implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
implementation "org.slf4j:slf4j-simple:2.0.7"
```

> Android **不要**使用 `netty-all`，见下文。

## PluginClient（插件）

连上后会自动 `register`，成功后再自动上报 `actions`。业务全部写在 `PluginListener`。

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

## 本地联调

先起服务端再起插件。消息均为一行 JSON，以 `\n` 结束。

仓库里的示例（在 `src/test/java`）：

- 服务端：`com.ultrabar.plugin.model.ServerMain`
- 插件：`com.ultrabar.plugin.model.ClientMain`

## 集成到 Android（PluginClient）

手机端只放 **PluginClient**。jar 本身没有打进第三方库，Android 模块需要同时引入 SDK jar 和运行时依赖。

**不要使用 `netty-all`**（体积大，含 Linux native epoll，不适合 APK）。

### 1. 拷贝 jar

把 `build/libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar` 放到 Android 工程例如 `app/libs/`。

### 2. `app/build.gradle`

```groovy
android {
    defaultConfig {
        minSdk 26   // 低于 26 需开启 coreLibraryDesugaring（本 SDK 使用 java.time）
    }
}

dependencies {
    implementation files("libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar")

    implementation "io.netty:netty-buffer:4.1.94.Final"
    implementation "io.netty:netty-codec:4.1.94.Final"
    implementation "io.netty:netty-handler:4.1.94.Final"
    implementation "io.netty:netty-transport:4.1.94.Final"
    implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
    implementation "org.slf4j:slf4j-api:2.0.7"
}
```

不要把 `slf4j-simple` 打进 App；用 `slf4j-api` 再接 Android 日志实现即可。

`minSdk < 26` 时：

```groovy
compileOptions {
    coreLibraryDesugaringEnabled true
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
}
dependencies {
    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.1.4"
}
```

### 3. 权限

`AndroidManifest.xml`：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

真机调试时，`PluginClient` 的 host 填电脑局域网 IP，不要用 `127.0.0.1`。

### 4. R8 / ProGuard

模型是 public 字段 + Jackson，Release 必须 keep：

```
-keep class com.ultrabar.plugin.model.** { *; }
-keep class com.ultrabar.plugin.callback.** { *; }
-keep class com.ultrabar.plugin.PluginClient { *; }
```

### 5. 线程

`PluginListener` 回调不在 Android 主线程。更新 UI 请切回 main：

```java
new Handler(Looper.getMainLooper()).post(new Runnable() {
    @Override
    public void run() {
        // update views
    }
});
```

进程退出或离开页面时调用 `client.stop()`。

## 协议要点

- 信封：`type`、`requestId`、`timestamp`、`protocol`、`payload`；注册成功后客户端会带 `sessionId`、`auth`。
- `requestId` 为 UUID，一次 RPC 用一次。
- 动作 ID 字段统一为 `actionId`。
- 成对消息：`register` / `register_result`，`actions` / `actions_result`，`describe` / `describe_result`，`get_options` / `get_options_result`，`call` / `call_result`，`heartbeat` / `heartbeat_ack`。
- 异步执行可用 `sendAccepted`，再用 `sendTaskUpdate` 推 `task_update`。
