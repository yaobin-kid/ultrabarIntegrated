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
PluginClient client = new PluginClient("127.0.0.1", 39001);

RegisterPayload register = new RegisterPayload();
register.id = "com.ultrabar.music";
register.name = "Music";
register.version = "1.2.0";
register.packageName = "com.ultrabar.music"; // 服务端用这个做会话唯一键
client.setRegisterConfig(register);

ActionSummary play = new ActionSummary();
play.actionId = "music.play";
play.name = "播放音乐";
play.description = "在指定设备播放";
client.setActionsConfig(new ActionsPayload(Arrays.asList(play)));

client.setPluginListener(new PluginListener() {
    @Override
    public void onRegisterSuccess(RegisterResultPayload payload) {
        // payload.sessionId / sessionToken / heartbeat
    }

    @Override
    public void onRegisterFailed(Throwable t) { }

    @Override
    public void onActionsAck(ActionsResultPayload ack) { }

    @Override
    public void onActionsFailed(Throwable t) { }

    @Override
    public void onActionsUpdate(ActionsPayload update) { }

    @Override
    public void onDescribeSuccess(DescribeResultPayload result) { }

    @Override
    public void onDescribeError(Throwable t) { }

    @Override
    public void onDescribe(DescribePayload payload, DescribeResponder responder) {
        DescribeResultPayload result = new DescribeResultPayload();
        result.success = true;
        result.actionId = payload.actionId;

        DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
        device.id = "deviceId";
        device.name = "播放设备";
        device.required = true;
        device.type = ParameterType.SELECT;

        DescribeResultPayload.OptionSpec options = new DescribeResultPayload.OptionSpec();
        options.provider = OptionProvider.REMOTE;
        options.searchable = true;
        device.options = options;

        result.parameters = Arrays.asList(device);
        responder.sendSuccess(result);
    }

    @Override
    public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
        GetOptionsResultPayload result = new GetOptionsResultPayload();
        result.success = true;
        result.hasMore = false;
        Item item = new Item();
        item.value = "sp00-1";
        item.label = "客厅音箱";
        result.items = Arrays.asList(item);
        responder.sendSuccess(result);
    }

    @Override
    public void onCall(CallPayload payload, CallResponder responder) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("actionId", payload.actionId);
        data.put("status", "ok");
        responder.sendSuccess(data);
        // 耗时长：responder.sendAccepted(data, taskId, null);
        // 随后 client.sendTaskUpdate(update);
    }
});

client.startAsync();
// 进程退出时：client.stop();
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
