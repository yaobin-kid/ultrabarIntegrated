# Ultrabar 插件 SDK 使用指南

> 语言: **中文** | [English](./README.md) | [日本語](./README_ja.md)

> 适用版本：ultrabarIntegrated 1.0.12+（协议 version = 2）
> 目标读者：接入 LineOS（Ultrabar）的第三方插件开发者
> 前置要求：熟悉 Android Service、Gradle 依赖配置

---

## 目录

1. [SDK 概述](#1-sdk-概述)
2. [快速开始](#2-快速开始)
3. [核心概念与通信模型](#3-核心概念与通信模型)
4. [场景应用能力（动作注册与执行）](#4-场景应用能力动作注册与执行)
5. [A2UI 生成式 UI](#5-a2ui-生成式-ui)
6. [模块事件（DOT_KEY / KNOB_KEY）](#6-模块事件dot_key--knob_key)
7. [热键与消息](#7-热键与消息)
8. [回调速查表](#8-回调速查表)
9. [注意事项与常见问题](#9-注意事项与常见问题)

---

## 1. SDK 概述

Ultrabar 插件 SDK 基于 Netty 实现插件 App 与 LineOS 之间的双向通信。传输协议为**一行一条 UTF-8 JSON**（`\n` 分帧），所有请求/响应均为异步模型（`CompletableFuture`）。

| 角色               | 类                                 | 职责                                                                   |
| ------------------ | ---------------------------------- | ---------------------------------------------------------------------- |
| 插件侧（你的 App） | `com.ultrabar.plugin.PluginClient` | 注册、上报 Actions、处理 describe / get_options / call、上报 A2UI 数据 |
| LineOS 侧          | `com.ultrabar.server.PluginServer` | 管理插件会话、向插件发起调用、缓存 A2UI 数据                           |

**作为第三方开发者，你只需要集成 `PluginClient`。** LineOS 会定期扫描 `AndroidManifest.xml` 中声明的插件服务并自动启动（已启动则跳过）。

插件 SDK 提供四大能力：

1. **场景应用能力**：插件向LineOS 声明"我能做什么"（如播放、暂停），LineOS 在用户触发时回调插件执行。
2. **A2UI 生成式 UI**：插件用 JSON 描述 UI 结构与数据，LineOS 渲染为原生界面，用户交互回传给插件。
3. **模块事件**：接收LineOS 硬件模块（DOT_KEY 按键、KNOB_KEY 旋钮等）的实时事件。
4. **热键与消息**：向 PC 端注入按键（单键/组合键/长按），或与 LineOS 双向传递消息（支持字符串与结构化对象）。

---

## 2. 快速开始

### 2.1 引入依赖

`build.gradle`：

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.yaobin-kid:ultrabarIntegrated:1.1.0'
    implementation "io.netty:netty-all:4.1.94.Final"
    implementation "com.fasterxml.jackson.core:jackson-databind:2.15.2"
    implementation "org.slf4j:slf4j-simple:2.0.7"
}

android {
    packagingOptions {
        exclude 'META-INF/INDEX.LIST'
        exclude 'META-INF/io.netty.versions.properties'
    }
}
```

> ⚠️ `packagingOptions` 的两个 `exclude` **必须配置**，否则打包时 Netty 资源文件冲突导致构建失败。

### 2.2 配置 AndroidManifest

声明一个后台服务，LineOS 通过该服务发现并拉起你的插件：

```xml
<manifest ...>
    <!-- 网络权限（本机回环通信也需要） -->
    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        <service
            android:name=".service.BackgroundService"
            android:enabled="true"
            android:exported="true"
            android:permission="com.ultrabar.plugin.SERVER_REGISER_PERMISSION">
            <!-- value 必须等于你的 applicationId -->
            <meta-data android:name="ultrabar.plugin" android:value="com.example.music" />
        </service>
    </application>
</manifest>
```

**三项强制要求：**

| 配置                      | 说明                                                                     |
| ------------------------- | ------------------------------------------------------------------------ |
| `meta-data android:value` | 必须等于 `applicationId`，LineOS 用它作为插件唯一标识（packageName）     |
| `android:exported="true"` | 必须导出，否则 LineOS 无法拉起服务                                       |
| `android:permission`      | 固定为 `com.ultrabar.plugin.SERVER_REGISER_PERMISSION`，防止其他应用冒充 |

### 2.3 最小可运行 Demo

在服务 `onCreate()` 中完成 `PluginClient` 的初始化与启动：

```java
public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String CHANNEL_ID = "plugin_bg_channel";

    private PluginClient client;

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 8.0+ 前台服务要求：通知渠道 + startForeground
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("插件服务运行中")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .build();
        startForeground(1001, notification);

        startPluginClient();
    }

    private void startPluginClient() {
        client = new PluginClient(); // 默认连接 127.0.0.1:39001

        // 1. 注册信息：name 为展示名，packageName 必须等于 applicationId
        RegisterPayload rp = new RegisterPayload();
        rp.name = "示例音乐插件";
        rp.packageName = "com.example.music";
        client.setRegisterConfig(rp);

        // 2. 声明支持的 Action 列表
        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "播放";
        play.description = "在指定设备上播放音乐";

        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "暂停";
        pause.description = "暂停指定设备的播放";

        client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause)));

        // 3. 设置监听器（所有回调均为可选 override）
        client.setPluginListener(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload result) {
                Log.d(TAG, "注册成功, 调试端口: " + result.configServer.port);
            }

            @Override
            public void onRegisterFailed(Throwable t) {
                Log.e(TAG, "注册失败: " + t.getMessage());
            }

            @Override
            public void onCall(CallPayload call, CallResponder responder) {
                if ("music.play".equals(call.actionId)) {
                    // 执行播放……
                }
                responder.sendSuccess(null);
            }
        });

        // 4. 异步启动：连接 → 注册 → 上报 Actions，全自动完成
        client.startAsync();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "插件后台服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (client != null) client.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

完成以上三步，你的插件即可被 LineOS 发现并与之通信。

---

## 3. 核心概念与通信模型

### 3.1 连接生命周期

```
startAsync()
   │
   ▼
TCP 连接（127.0.0.1:39001）
   │
   ▼
REGISTER ─────────► LineOS 校验 packageName
   ◄───────── REGISTER_RESULT（sessionId / sessionToken / configServer.port）
   │
   ▼
ACTIONS ──────────► 上报动作列表
   ◄───────── ACTIONS_RESULT（success / receivedCount）
   │
   ▼
会话就绪 ◄──── onRegisterSuccess 已触发
   │
   ▼
心跳保活（自动）＋ 断线自动重连（重连后自动重新注册并上报 Actions）
```

**关键点：**

- `startAsync()` 返回 `CompletableFuture<Void>`，连接建立即完成；注册结果通过 `onRegisterSuccess` / `onRegisterFailed` 回调通知。
- SDK 内置心跳与自动重连。**重连成功后会自动重新注册并重新上报 Actions，无需手动处理。**
- `onRegisterSuccess` 中的 `payload.configServer.port` 是LineOS 分配的 HTTP 调试端口。如需提供插件调试页面，在该端口启动你的 HTTP Server。

### 3.2 sessionToken 安全

`onRegisterSuccess` 会下发 `sessionToken`——当前调试会话的身份凭证。LineOS 打开你的调试页面时会携带它。

> ⚠️ **你必须自行校验 Token 的合法性**，不要默认信任任何携带 Token 的请求。

### 3.3 线程模型

所有 `PluginListener` 回调均运行在 SDK 的回调线程池（非 Android 主线程）。**涉及 UI 操作时请自行切换到主线程：**

```java
new Handler(Looper.getMainLooper()).post(() -> {
    // UI 操作
});
```

### 3.4 会话唯一性

LineOS 按 `packageName` 唯一标识一个插件会话。同一 `packageName` 再次注册会顶掉旧连接。

---

## 4. 场景应用能力（动作注册与执行）

Action 是插件对LineOS 暴露的"能力点"。完整链路分四步：

```
插件上报 actionId 列表 ──► LineOS 询问参数描述(onDescribe)
   ──► （可选）LineOS 拉取动态选项(onOptions) ──► 用户触发后LineOS 回调执行(onCall)
```

### 4.1 注册动作

```java
ActionSummary play = new ActionSummary();
play.actionId = "music.play";        // 全局唯一 ID，建议 "域名.动词" 格式
play.name = "播放";                   // LineOS 展示名
play.description = "在指定设备上播放音乐";

client.setActionsConfig(new ActionsPayload(Arrays.asList(play, pause, stop)));
```

运行期动作列表变化时调用：

```java
client.updateActions(new ActionsPayload(newList));
```

上报结果通过 `onActionsAck(ActionsResultPayload ack)` 通知：

```java
@Override
public void onActionsAck(ActionsResultPayload ack) {
    // ack.success     —— LineOS 是否成功接收
    // ack.receivedCount —— 实际接收到的动作数量
    // 注意：这是"配置同步"的 ACK，不是 onCall 的执行结果
}
```

### 4.2 参数描述 onDescribe

LineOS 在调用 Action 前，先通过 `onDescribe` 获取该 Action 需要哪些参数（用于渲染配置界面）：

```java
@Override
public void onDescribe(DescribePayload payload, DescribeResponder responder) {
    DescribeResultPayload result = new DescribeResultPayload();
    result.actionId = payload.actionId;
    result.success = true;
    result.parameters = new ArrayList<>();

    if ("music.play".equals(payload.actionId)) {
        // 参数 1：设备选择（静态选项）
        DescribeResultPayload.ParameterSpec device = new DescribeResultPayload.ParameterSpec();
        device.id = "deviceId";            // ★ 关键：onCall 中用这个 id 取值
        device.name = "设备";
        device.placeholder = "请选择设备";
        device.required = true;
        device.type = ParameterType.SELECT;

        DescribeResultPayload.OptionSpec deviceOpts = new DescribeResultPayload.OptionSpec();
        deviceOpts.provider = OptionProvider.STATIC;   // 静态选项，随描述一起下发
        deviceOpts.searchable = false;
        deviceOpts.items = new ArrayList<>();
        deviceOpts.items.add(new Label("客厅播放器", "test01"));
        deviceOpts.items.add(new Label("卧室播放器", "test02"));
        device.options = deviceOpts;

        // 参数 2：歌曲名称（文本输入）
        DescribeResultPayload.ParameterSpec title = new DescribeResultPayload.ParameterSpec();
        title.id = "title";
        title.name = "歌曲名称";
        title.placeholder = "输入歌曲名称";
        title.required = true;
        title.type = ParameterType.TEXT;

        // 参数 3：输入源（动态选项，依赖 deviceId）
        DescribeResultPayload.ParameterSpec source = new DescribeResultPayload.ParameterSpec();
        source.id = "source";
        source.name = "输入源";
        source.required = true;
        source.type = ParameterType.SELECT;
        DescribeResultPayload.OptionSpec sourceOpts = new DescribeResultPayload.OptionSpec();
        sourceOpts.provider = OptionProvider.REMOTE;   // 动态选项，触发 onOptions 回调
        sourceOpts.searchable = false;
        source.options = sourceOpts;
        source.dependsOn = new ArrayList<>();
        source.dependsOn.add("deviceId");              // 依赖参数：deviceId 变化时重新拉取

        result.parameters.add(device);
        result.parameters.add(title);
        result.parameters.add(source);
    }

    responder.sendSuccess(result);
}
```

### 4.3 动态选项 onOptions

仅当参数声明了 `OptionProvider.REMOTE` 时，LineOS 才会回调此方法拉取选项：

```java
@Override
public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
    GetOptionsResultPayload result = new GetOptionsResultPayload();
    result.success = true;
    result.hasMore = false;
    result.nextCursor = null;

    if ("music.play".equals(payload.actionId) && "source".equals(payload.describeId)) {
        result.items = new ArrayList<>();
        result.items.add(new Label("QQ 音乐", "qq"));
        result.items.add(new Label("网易云音乐", "163"));
    }

    responder.sendSuccess(result);
}
```

- `payload.actionId`：当前 Action；`payload.describeId`：当前参数的 id。
- 选项量大时，用 `hasMore = true` + `nextCursor` 实现分页，LineOS 翻页时会在请求中带上 `cursor`。

### 4.4 执行回调 onCall

用户在LineOS 中触发 Action 后回调这里：

```java
@Override
public void onCall(CallPayload payload, CallResponder responder) {
    Map<String, Object> params = payload.params;

    if ("music.play".equals(payload.actionId)) {
        String deviceId = (String) params.get("deviceId"); // key 必须与 ParameterSpec.id 一致
        String title = (String) params.get("title");
        String source = (String) params.get("source");
        // TODO: 执行真实播放逻辑
    }

    // 返回值（可选），LineOS 可收到该 Map
    Map<String, Object> data = new HashMap<>();
    data.put("status", "ok");
    responder.sendSuccess(data);

    // 失败时：
    // responder.sendError("PLAY_FAILED", "设备离线", false);
}
```

> ⚠️ **`params` 的 key 必须与 `onDescribe` 中定义的 `ParameterSpec.id` 完全一致**，这是两端的隐式契约。

---

## 5. A2UI 生成式 UI

A2UI 让插件用 JSON 描述 UI，由LineOS 渲染为原生界面：

- **插件 → LineOS**：上报 UI 结构（surface 创建 / 组件树）、更新数据模型
- **LineOS → 插件**：用户交互（按钮点击等）以 action 形式回传

> 📖 **重要：协议与渲染细节以原始库文档为准**
>
> 本 SDK（ultrabarIntegrated）只负责**插件 ↔ LineOS 之间的 A2UI 消息传输通道**。A2UI JSON 的格式规范、组件目录、数据绑定语法、Action 语义，由LineOS 集成的渲染库 **[a2ui-android](https://github.com/yaobin-kid/a2ui-android)** 实现并定义。
>
> **书写 A2UI JSON 前，请务必阅读原始库文档：[https://github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android)**
>
> - 协议版本：**A2UI v0.9.1**
> - 官方规范：[A2UI v0.9.1 Specification](https://a2ui.org/specification/v0.9-a2ui/)
> - Basic Catalog 共 **18 类组件**：布局（Row、Column、List、Card、Tabs、Modal）、展示（Text、Image、Icon、Video、AudioPlayer、Divider）、输入（Button、TextField、CheckBox、ChoicePicker、Slider、DateTimeInput）

> ⚠️ **协议版本强制要求**：所有 A2UI JSON 必须是 **v0.9.1** 格式，每条 envelope 必须带 `"version": "v0.9.1"` 字段。不要使用 v0.8 的 `beginRendering` / `surfaceUpdate` / `dataModelUpdate` 消息名。

### 5.1 启用 A2UI

注册时声明 `supportsA2UI = true`：

```java
RegisterPayload rp = new RegisterPayload();
rp.name = "示例插件";
rp.packageName = "com.example.music";
rp.supportsA2UI = true;   // ★ 关键开关
client.setRegisterConfig(rp);
```

**不声明的后果：** LineOS 调用 `callA2UIAction` / `queryA2UIData` / `notifyA2UIRender` 会被服务端直接拒绝；但插件主动向LineOS 上报数据不受影响。

### 5.2 A2UI 消息类型对照

| Envelope key       | 方向        | SDK API                   | 用途                   |
| ------------------ | ----------- | ------------------------- | ---------------------- |
| `createSurface`    | 插件→LineOS | `refreshA2UI`             | 创建 UI surface        |
| `updateComponents` | 插件→LineOS | `refreshA2UI`             | 定义/更新组件树        |
| `updateDataModel`  | 插件→LineOS | `sendA2UIUpdateDataModel` | 写入/更新/删除数据模型 |
| `deleteSurface`    | 插件→LineOS | `refreshA2UI`             | 删除 surface           |
| `action`（event）  | LineOS→插件 | 回调 `onA2UIActionCall`   | 用户交互回传           |
| 渲染通知           | LineOS→插件 | 回调 `onA2UIRender`       | 卡片首次渲染/刷新通知  |

### 5.3 刷新已存在视图 refreshA2UI

`refreshA2UI` 用于刷新**已经存在**的 A2UI 视图：通过 `instanceId + layoutId` 定位到已渲染的视图实例，用新的 `ui` 数据替换其内容。
（卡片首次定义由服务端 `queryA2UIData` 拉取，插件在 `onA2UIDataRequest` 返回完整 `A2UIItem`，见 §5.4。）

```java
// A2UIRefreshItem: instanceId（实例ID）+ layoutId（布局ID）+ ui（A2UI 消息列表）
// 3 个参数创建时必须全部传入；ui 为 JSON 数组（List<Map<String, Object>>）：
//   [{createSurface...}, {updateComponents...}, {updateDataModel}...]
// ui 支持重载：直接传结构化数组，或传 JSON 字符串由 SDK 内部解析
//（单条 JSON 对象字符串也会自动包装为单元素数组）
String ui = "["
    + "{\"version\":\"v0.9.1\",\"updateComponents\":{"
    + "   \"surfaceId\":\"example-focus\","
    + "   \"components\":["
    + "     {\"id\":\"root\",\"component\":\"Card\",\"child\":\"content\"},"
    + "     {\"id\":\"content\",\"component\":\"Text\","
    + "      \"text\":{\"path\":\"/headline\"},\"variant\":\"h3\"}"
    + "   ]}}"
    + "]";

// 方式一：字符串（内部自动解析为 JSON 数组）
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", ui))
      .thenAccept(ack -> Log.d(TAG, "刷新结果: " + ack.success));

// 方式二：直接构造结构化数组（推荐，避免字符串拼接出错）
Map<String, Object> updateComponents = new HashMap<>();
// ... 填充 A2UI 消息结构
client.refreshA2UI(new A2UIRefreshItem("inst-example", "example-focus", uiList));

// 批量刷新多个视图：
// client.refreshA2UI(Arrays.asList(item1, item2));
```

服务端收到后触发 `PluginServerListener.onA2UIRefresh(session, payload)` 回调；刷新是局部内容替换，**不会**覆盖 `PluginSession` 缓存的完整卡片定义（`a2uiData`）。

**书写要点（详见原始库文档）：**

- 根组件 ID **必须为 `root`**；组件之间通过 ID 引用（如 `Card.child = "content"`）
- `catalogId` 使用 v0.9.1 Basic Catalog：`https://a2ui.org/specification/v0_9_1/catalogs/basic/catalog.json`
- `createSurface.theme.primaryColor` 为 v0.9.1 主题字段（`#RRGGBB` / `#AARRGGBB`），作用于 Icon、Button、Tabs 及输入控件的选中/聚焦状态；缺省为 `#3F51B5`
- `createSurface.sendDataModel = true` 表示交互回传时携带整个 dataModel 快照

**建议时机：** 在 `onRegisterSuccess` 回调里上报当前 UI 快照，确保LineOS 拿到最新状态。

### 5.4 数据绑定（速览）

每个 Surface 内部维护一个 JSON 对象 `dataModel`。组件的动态属性通过 `{ "path": "/xxx" }` 绑定到 dataModel 字段，`updateDataModel` 更新后绑定组件自动重渲染。

**path 遵循 [RFC 6901 JSON Pointer](https://tools.ietf.org/html/rfc6901)：**

| 写法               | 含义                           |
| ------------------ | ------------------------------ |
| `"/message"`       | 根对象的 `message` 字段        |
| `"/user/name"`     | `user` 对象下的 `name` 字段    |
| `"/items/0/title"` | 数组 `items` 第 0 项的 `title` |
| `"/items/-"`       | 数组末尾追加位置（仅写入时）   |

转义规则：`~` 写成 `~0`，`/` 写成 `~1`。

**两种绑定写法（注意使用场景区分）：**

| 写法       | 形式                        | 适用位置                                                      |
| ---------- | --------------------------- | ------------------------------------------------------------- |
| 对象形式   | `{ "path": "/form/email" }` | 组件属性（`Text.text`、`Image.url`、`Slider.value` 等）       |
| 字符串简写 | `"#/form/email"`            | **仅限** `action.event.context` 和 `action.functionCall.args` |

**列表动态渲染：** `Row`/`Column`/`List` 的 `children` 支持 `{ "path": "/数组路径", "componentId": "模板组件ID" }`，引擎遍历数组为每项生成模板实例；模板组件内部可写**相对路径**（不带 `/`），自动拼接到当前数组项路径前。

> 📖 完整说明见原始库文档「[数据绑定](https://github.com/yaobin-kid/a2ui-android#4-数据绑定data-model-binding)」章节。

### 5.5 更新数据 sendA2UIUpdateDataModel

UI 结构不变、只更新绑定的数据时，无需重发整个 envelope。`updateDataModel` 消息体有三个字段：`surfaceId`（必填）、`path`（可选）、`value`（可选），对应三种用法。

方法签名：`sendA2UIUpdateDataModel(instanceId, layoutId, dataModel)`，另有带 `incremental` 的重载（默认 `true` 增量合并，`false` 整体替换）：

```java
// 用法一：不传 path，value 为对象 → 整体替换 dataModel
String full = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"value\":{\"headline\":\"新的标题\",\"progress\":\"2 / 3\"}"
    + "}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", full);

// 用法二：path + value → 只更新指定路径（中间路径不存在时自动创建）
String partial = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\","
    + "\"path\":\"/progress\",\"value\":\"3 / 3\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", partial);

// 向数组追加元素（path 用 /items/-）：
// "{\"path\":\"/items/-\",\"value\":{\"title\":\"新任务\"}}"

// 用法三：有 path 无 value → 删除该路径
String del = "{\"version\":\"v0.9.1\",\"updateDataModel\":{"
    + "\"surfaceId\":\"example-focus\",\"path\":\"/progress\"}}";
client.sendA2UIUpdateDataModel("inst-example", "example-focus", del);

// 整体替换（不合并旧数据）：
// client.sendA2UIUpdateDataModel("inst-example", "example-focus", full, false);
```

**检查处理结果：** 返回值 `A2UIUpdateDataModelResultPayload` 中 `success=false` 表示LineOS 处理失败，插件侧应检查 ack 而不是盲目认为成功：

```java
client.sendA2UIUpdateDataModel("inst-example", "example-focus", update).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "数据更新被LineOS 拒绝: " + (ack.error != null ? ack.error.message : "unknown"));
    }
});
```

### 5.6 事件回传 onA2UIActionCall

A2UI 组件的 `action` 有**三种形式**，只有 `event` 形式会回传到插件：

| 形式           | 触发组件                                 | 执行方                                                       | 是否回传插件            |
| -------------- | ---------------------------------------- | ------------------------------------------------------------ | ----------------------- |
| `event`        | Button / Modal trigger                   | LIneOS → **转发给插件**                                      | ✅ 走 `onA2UIActionCall` |
| `functionCall` | Button / 任意组件                        | Surface 内置/注册函数（如 `formatString`、`formatCurrency`） | ❌ 在渲染层内部执行      |
| `input`        | TextField / CheckBox / Slider 等输入控件 | Surface 内部（写回 dataModel）                               | ❌ 自动写回 dataModel    |

**插件要接收用户交互，必须使用 `event` 形式：**

```json
{
  "id": "submit",
  "component": "Button",
  "variant": "filled",
  "child": "submitText",
  "action": {
    "event": {
      "name": "submitForm",
      "context": {
        "email": "#/form/email",
        "agreed": "#/form/agreed"
      }
    }
  }
}
```

**字段映射关系（event → A2UIActionCallPayload）：**

| A2UI event 字段 | 插件侧 payload 字段  | 说明                                                                                   |
| --------------- | -------------------- | -------------------------------------------------------------------------------------- |
| `event.name`    | `payload.actionName` | 事件名，如 `"submitForm"`                                                              |
| `event.context` | `payload.context`    | `Map<String, Object>`；其中 `#/path` 引用**已被替换为 dataModel 实际值**，无需二次解析 |
| `surfaceId`     | `payload.layoutId`   | 事件来源的布局 id                                                                      |
| ——              | `payload.instanceId` | 事件来源的卡片实例 id                                                                  |
| ——              | `payload.cardId`     | 事件来源的卡片 id                                                                      |

插件端接收：

```java
@Override
public void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
    String actionName = payload.actionName;          // event.name，如 "submitForm"
    Map<String, Object> context = payload.context;   // event.context（#/path 已解析为实际值）
    String instanceId = payload.instanceId;          // 来源卡片实例
    String layoutId = payload.layoutId;              // 来源 surfaceId

    if ("submitForm".equals(actionName)) {
        String email = context == null ? null : (String) context.get("email");
        // TODO: 处理交互逻辑

        Map<String, Object> reply = new HashMap<>();
        reply.put("handled", true);
        responder.sendSuccess(reply);

        // 处理完成后通常需要刷新界面：
        // client.sendA2UIUpdateDataModel(instanceId, layoutId, updateJson);
        return;
    }

    // 不认识的 action：返回 NO_HANDLER，LineOS 可走兜底逻辑
    responder.sendError("NO_HANDLER", "no handler for " + actionName, false);
}
```

> 📖 三种 action 的完整语义、内置函数列表、自定义函数注册，见原始库文档「[Action 用法](https://github.com/yaobin-kid/a2ui-android#34-action-用法button-交互)」章节。

### 5.7 LineOS 主动拉取 onA2UIDataRequest

除插件主动推送外，LineOS 也会主动拉取插件当前的 A2UI 数据（例如注册成功后自动拉取一次）。实现该回调返回当前快照：

```java
@Override
public List<A2UIItem> onA2UIDataRequest() {
    List<A2UIItem> items = new ArrayList<>();
    // A2UIItem 4 参必填：layoutId / cardId / name / ui
    items.add(new A2UIItem("example-focus", "card-example", "示例", currentUiJson));
    return items;   // 返回 null 或空列表表示当前无数据
}
```

> 📌 拉取成功后，LineOS 自动将数据缓存到插件会话。若未实现此回调，默认返回空列表。

### 5.8 卡片参数 schema（args）

`A2UIItem.args` 用于声明用户可配置哪些字段、用什么控件渲染。6 种类型各有**专属 Builder**，只能设置本类型自己的字段（编译期隔离，无法交叉设置）：

| 类型     | Builder       | 可设置                                                                             |
| -------- | ------------- | ---------------------------------------------------------------------------------- |
| `int`    | `intArg()`    | `label` `required` `defaultValue(int)` `min(int)` `max(int)`                       |
| `float`  | `floatArg()`  | `label` `required` `defaultValue(double)` `min(double)` `max(double)`              |
| `bool`   | `boolArg()`   | `label` `required` `defaultValue(boolean)`                                         |
| `string` | `stringArg()` | `label` `required` `defaultValue(String)`                                          |
| `select` | `selectArg()` | `label` `required` `defaultValue(String)` `option(value)` / `option(value, label)` |
| `object` | `objectArg()` | `label` `required` `property(name, A2UIArgSpec)`（递归嵌套）                       |

```java
A2UIArgs args = A2UIArgs.create()
        .arg("title", A2UIArgSpec.stringArg().label("标题").defaultValue("参数演示").build())
        .arg("count", A2UIArgSpec.intArg().label("数量").defaultValue(3).min(1).max(99).build())
        .arg("level", A2UIArgSpec.selectArg().label("等级").defaultValue("normal")
                .option("low", "低").option("normal", "中").build())
        .arg("headers", A2UIArgSpec.objectArg().label("嵌套参数组")
                .property("retries", A2UIArgSpec.intArg().defaultValue(3).max(10).build())
                .build());

// A2UIItem 用于 onA2UIDataRequest 查询返回（含 args 参数 schema）
new A2UIItem("example-focus", "card-example", "示例", ui, args);
```

**动态 schema**：参数由插件实时决定时，声明 `dynamic + schemaGetter`；LineOS 在渲染表单前 拉取参数，插件在 `PluginListener.onA2UIArgsSchemaRequest(schemaGetter)` 中直接返回 `A2UIArgs`：

```java
// 插件声明：动态参数
A2UIArgs args = A2UIArgs.dynamic("schema.argsdemo");

// 插件侧：响应拉取，实时生成 A2UIArgs
@Override
public A2UIArgs onA2UIArgsSchemaRequest(String schemaGetter) {
    return A2UIArgs.create()
            .arg("fieldA", A2UIArgSpec.stringArg().label("字段A").defaultValue("v1").build())
            .arg("fieldB", A2UIArgSpec.intArg().label("字段B").min(1).max(100).build());
    // 返回 null 表示没有可配置参数，服务端会归一化为空 A2UIArgs
}
```

静态字段与动态声明可共存：固定字段先渲染，动态字段由 getter 返回的 schema 合并追加。

### 5.9 渲染通知 onA2UIRender

LineOS 在卡片**首次渲染**和**刷新渲染**时，会主动通知插件（`PluginServer.notifyA2UIRender`），插件在 `PluginListener.onA2UIRender` 接收：

```java
@Override
public void onA2UIRender(A2UIRenderPayload payload, A2UIRenderResponder responder) {
    // payload.instanceId  卡片实例 id
    // payload.layoutId    布局 id
    // payload.cardId      卡片 id
    // payload.args        本次渲染生效的 args 参数值（Map，可能为 null）
    // payload.stage       FIRST_RENDER 首次渲染 / REFRESH 刷新渲染

    if (payload.stage == A2UIRenderStage.FIRST_RENDER) {
        // 首次渲染：按需拉取初始数据、刷新内容等
    }

    responder.sendSuccess();
}
```

服务端侧调用：

```java
server.notifyA2UIRender(packageName, instanceId, layoutId, cardId, argsMap,
        A2UIRenderStage.FIRST_RENDER);
```

> 📌 未实现该回调时默认返回 `NO_HANDLER`；插件必须声明 `supportsA2UI=true` 才会收到通知。

### 5.10 参考资料

| 资料                  | 链接                                                                             | 说明                                                               |
| --------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| a2ui-android 渲染库   | [github.com/yaobin-kid/a2ui-android](https://github.com/yaobin-kid/a2ui-android) | **A2UI JSON 书写权威文档**：组件目录、样式、数据绑定、Action、函数 |
| A2UI 官方规范         | [a2ui.org/specification/v0.9-a2ui](https://a2ui.org/specification/v0.9-a2ui/)    | 协议规范原文                                                       |
| RFC 6901 JSON Pointer | [tools.ietf.org/html/rfc6901](https://tools.ietf.org/html/rfc6901)               | 数据绑定 path 语法                                                 |

---

## 6. 模块事件（DOT_KEY / KNOB_KEY）

LineOS 的硬件模块（按键、旋钮等）产生的事件可实时分发给插件。

### 6.1 订阅模块事件

```java
// 可变参数，传入需要的模块类型
client.subscribeModuleEvents(ModuleType.DOT_KEY, ModuleType.KNOB_KEY);

// 取消订阅
// client.unsubscribeModuleEvents(ModuleType.DOT_KEY);
```

> 📌 订阅关系保存在LineOS 侧的会话中；断线重连后 SDK 会自动重新订阅，无需手动处理。

### 6.2 分发规则（重点）

模块事件**不是广播**，而是LineOS 按优先级**串行单播**：从候选插件列表中依次询问，直到有一个插件"消费"该事件为止；无人消费则进入兜底流程。

完整分发流程：

```
模块事件产生（按键按下 / 旋钮转动）
   │
   ▼
① 筛选候选插件
   · 已调用 subscribeModuleEvents 订阅该 ModuleType
   · 且当前连接存活
   │
   ▼
② 候选排序（优先级从高到低）
   · 前台插件优先，多个前台插件按"最近前台时间"倒序
   · 后台插件在后，多个后台插件按"最近活跃时间"倒序
   │
   ▼
③ 串行单播：按排序依次发给候选插件
   · 每个插件收到的事件中 appState 字段标记的是"它自己"的前后
     台状态（foreground / background）
   · 每个候选有独立的回复超时（默认 1000ms）
   │
   ├── 插件调用 responder.consume() ──► ④a 分发结束
   │                                    结果：handled，记录消费的包名
   │
   ├── 插件调用 responder.ignore() ───► 尝试下一个候选
   │
   └── 超时未回复（视同 ignore）──────► 尝试下一个候选
   │
   ▼（所有候选都未消费）
⑤ 兜底流程：事件 appState 置为 none
   ├── 该 ModuleType 若注册过 registerFallback 回调
   │   ──► 依次执行全部已注册的兜底回调
   └── 否则 ──► PluginServerListener.onModuleEventFallback(event)
```

**规则要点：**

| 规则            | 说明                                                                                                   |
| --------------- | ------------------------------------------------------------------------------------------------------ |
| 单播而非广播    | 一个事件最多只被一个插件消费；排在前面的插件 `consume()` 后，后面的插件**收不到**该事件                |
| 优先级          | 前台 > 后台；同类中最近前台/最近活跃者优先。                                                           |
| appState 语义   | 事件里的 `appState` 是**接收方自己的状态**（foreground/background），不是事件源的状态；兜底时为 `none` |
| 不回复 = ignore | `onModuleEvent` 中既不 `consume()` 也不 `ignore()`，超时（默认 1s）后自动视为放弃，事件流向下一候选    |





### 6.3 接收事件 onModuleEvent

```java
@Override
public void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
    // payload.appState 是当前插件自己的前后台状态（见 6.2 分发规则）
    if (ModuleEventPayload.APP_STATE_FOREGROUND.equals(payload.appState)) {
        // 前台：消费事件，分发立即终止，后续插件与兜底流程都不再触发
        responder.consume();
        // TODO: 处理按键/旋钮事件 payload.event
    } else {
        // 后台：显式放弃，事件立即流向下一候选（避免 1s 超时等待）
        responder.ignore(null);
    }
}
```

**事件处理规则：**

- `responder.consume()` —— 插件消费该事件，分发终止
- `responder.ignore(null)` —— 显式放弃，**立即**流向下一候选
- 两者都不调 —— 超时（默认 1s）后被动放弃，同样流向下一候选；**建议显式 ignore，避免拖慢整条分发链**

### 6.4 事件数据格式

**DOT_KEY（按键）：**

```json
{
    "appState": "foreground",
    "module": "DOT_KEY",
    "action": "button",
    "event": {
        "action": 1,
        "downTime": 169360339,
        "eventTime": 169360339,
        "keyCode": 2,
        "repeatCount": 0
    },
    "moduleId": "4",
    "sn": "xxxxx",
    "timestamp": 1793002523558
}
```

- `event.action`：1=按下，0=抬起
- `event.keyCode`：按键码，取值 0 / 1 / 2
- `event.repeatCount`：长按递增

**KNOB_KEY（旋钮 + 按键）：**

```json
// 旋钮事件（转得越快报得越频繁）
{
    "appState": "foreground",
    "module": "KNOB_KEY",
    "action": "rotate",
    "event": { "value": 1 },
    "moduleId": "6",
    "sn": "xxxx",
    "timestamp": 1793005179031
}
```

- `action`：`button`=按键（格式同 DOT_KEY），`rotate`=旋转
- `event.value`：1=顺时针，-1=逆时针

**SENSE_CUBE（多功能传感魔方）：**

光感（`action = "illuminance"`）：

```json
{
    "action": "illuminance",
    "appState": "background",
    "event": {
        "level": "BRIGHT",
        "unit": "Lux",
        "value": 122
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449768487
}
```

温度（`action = "temperature"`）：

```json
{
    "action": "temperature",
    "appState": "background",
    "event": {
        "level": "MILD",
        "unit": "°F",
        "value": 74.66
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449766512
}
```

存在感应（`action = "presence"`）：

```json
{
    "action": "presence",
    "appState": "background",
    "event": true,
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794449766412
}
```

湿度（`action = "humidity"`）：

```json
{
    "action": "humidity",
    "appState": "background",
    "event": {
        "unit": "%",
        "value": 38.9
    },
    "module": "SENSE_CUBE",
    "moduleId": "26",
    "sn": "0000019F20E45C31",
    "timestamp": 1794450176184
}
```

- `action`：`illuminance`=光感、`temperature`=温度、`presence`=存在感应、`humidity`=湿度
- `event.value`：当前传感器数值（`presence` 为 `boolean`，true=有人）
- `event.unit`：计量单位（Lux / °F / %）
- `event.level`：体感/光照等级（如 `BRIGHT`、`MILD`），部分事件存在该字段

### 6.5 查询模块列表 queryModules

```java
// 查询全部
client.queryModules().thenAccept(result -> {
    for (ModuleInfo m : result.modules) {
        Log.d(TAG, m.module + " / " + m.moduleId + " / " + m.name
                + " / online=" + m.online + " / sn=" + m.sn);
    }
});

// 只查在线 / 离线
// client.queryModules(ModuleOnlineFilter.ONLINE);
// client.queryModules(ModuleOnlineFilter.OFFLINE);
```

返回结构：

```json
{
    "modules": [
        {
            "module": "DOT_KEY",
            "moduleId": "37",
            "name": "Dot Key",
            "online": true,
            "sn": "0000019E4F84ABEF"
        }
    ],
    "success": true
}
```

> 目前仅支持 `DOT_KEY`、`KNOB_KEY`、`SENSE_CUBE` 三种模块。

---

## 7. 热键与消息

本章介绍两种插件 ↔ PC 端的直通能力：

- **热键**：向 PC 端注入键盘按键，支持单键、组合键与长按（如模拟 Windows 的 Ctrl+C 复制）；
- **消息通道**：插件与 LineOS 之间双向传递消息（按实例 id 寻址，内容支持字符串与结构化对象）。

### 7.1 按键码约定

热键 API 的参数是**原始按键码（int）**，与宿主平台一致——PC 端即 Windows VK 虚拟键码。按键码完整对照表见 [hidKey.md](./hidKey.md)。

### 7.2 短按 sendHotkey

`sendHotkey(int... keys)` 表示一次完整的"按下并抬起"，**SDK 内部自动补齐 down + up**，无需配对调用：

```java
// 单键：按一下 F1
client.sendHotkey(0x70);

// 组合键：模拟 Windows 复制 Ctrl+V（修饰键在前）
client.sendHotkey(0xE0, 0x19).thenAccept(ack -> {
    if (!ack.success) {
        Log.e(TAG, "热键执行失败: " + ack.error.message);
    }
});
```

语义规则：

- 组合键作为一个整体**一次性**发送（keys 数组打包进同一条消息），down 按参数顺序、up 按**逆序**（修饰键先按后抬，与真实键盘一致）；
- 每次调用在服务端产生 2 次回调（一条 down + 一条 up），消息 `pressType = short`；
- 返回的 `CompletableFuture` 是 up 的回执（channel 写有序，前面的 down 已送达）；`success=false` 表示宿主执行失败。

### 7.3 长按 sendHotkeyDown / sendHotkeyUp

需要"按住"语义时，使用 `sendHotkeyDown` / `sendHotkeyUp` 显式配对，消息 `pressType = long`：

```java
client.sendHotkeyDown(0x5E);   // 按下（不抬起）
// ...长按中...
client.sendHotkeyUp(0x5E);     // 抬起
```

**自动补发保护**：`sendHotkeyDown` 后若超过 3000ms（可用 `client.setHotkeyAutoReleaseMs(...)` 调整，设 0 关闭）仍未调用 `sendHotkeyUp`，SDK 会自动补发一次 up，防止按键在系统侧卡死。显式 `sendHotkeyUp` 会取消该任务。

**持续生效场景**（如 PS 中 Ctrl+] 按住时画笔持续线性放大）：真实键盘的"持续生效"来自硬件自动重复——按住不放会不断产生 down 事件。SDK 注入只发一次 down 不会自动重复，需要以固定间隔重复发送：

```java
client.setHotkeyAutoReleaseMs(0);          // 按住期间关闭自动补发
client.sendHotkeyDown(0x11);                   // 1. Ctrl 按下，只发一次

while (holding) {                          // 2. 按住期间：每 80ms 重复发送 ] 的 down
    client.sendHotkeyDown(0xDD);               //    模拟键盘自动重复 → 画笔持续放大
    Thread.sleep(80);
}

client.sendHotkeyUp(0x11, 0xDD);               // 3. 结束：传参与 down 同序，SDK 内部逆序抬起（先 ] 后 Ctrl）
```

> 重复 `sendHotkeyDown` 同一个键会**重置**自动补发计时——只要重复间隔小于补发阈值，默认配置下也不会误触发补发。

### 7.4 发送消息 sendMessage

向指定插件实例发送消息（`message` 为 `Object`，支持字符串、Map、List、POJO 等任意可序列化对象），默认透传到 Ultra WorkSpace PLUGIN 端：

```java
// 默认目的地 ULTRA_WORKSPACE_PLUGIN —— 发送字符串
client.sendMessage("plugin-001", "开始播放")
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "消息投递失败: " + ack.error.message);
          }
      });

// 发送结构化对象（接收端拿到的是 Map）
Map<String, Object> body = new HashMap<>();
body.put("action", "play");
body.put("trackId", 42);
client.sendMessage("plugin-001", body);

// 显式指定透传目的地（MessageDestination 枚举）
client.sendMessage(MessageDestination.ULTRA_WORKSPACE_PLUGIN, "plugin-002", "切换频道");
```

- `destination` 为 `MessageDestination` 枚举（当前含 `ULTRA_WORKSPACE_PLUGIN`），后续新增目的地只需扩展枚举，API 不变；
- `pluginId` 为空、`message` / `destination` 为 null 会在客户端本地抛 `IllegalArgumentException`，不发起请求；空字符串 `""` 是合法消息内容；
- 服务端收到后回调 `PluginServerListener.onMessage`，其返回值作为 ack 回传——`success=false` 表示宿主处理失败。

### 7.5 接收 LineOS 下发的消息 onMessage

消息通道是双向的。LineOS 侧调用 `PluginServer.sendMessage(packageName, pluginId, message)` 时，插件通过 `PluginListener.onMessage` 接收：

```java
client.setPluginListener(new PluginListener() {
    @Override
    public void onMessage(MessagePayload payload, MessageResponder responder) {
        String pluginId = payload.pluginId;       // 目标插件实例 id
        Object message = payload.message;         // 消息内容（可能为 String / Map / List 等）
        // payload.destination 在此方向为 null（仅插件→LineOS 方向使用）

        if (message instanceof String) {
            String text = (String) message;
        } else if (message instanceof Map) {
            Map<String, Object> body = (Map<String, Object>) message;
        }

        // 处理后必须响应，二选一：
        responder.sendSuccess();
        // responder.sendError("NOT_SUPPORTED", "该实例不支持消息", false);
    }
});
```

> 默认实现回 `NO_HANDLER` 错误；不需要此能力时可不 override。

### 7.6 请求宿主执行动作 sendHostAction

打开文件、启动应用、打开浏览器、输入文本、执行命令这 5 类操作统一为**一个方法**，每个动作有**专属 Builder**——只能设置该动作自己的参数（编译期隔离，无法交叉设置），参数键由 SDK 内聚，调用方不需要关心协议字段名：

```java
// 1. 打开文件或文件夹（只能设置 path）
client.sendHostAction(HostActionRequest.openFile()
        .path("C:/Users/kid/Desktop/ap")
        .build());

// 2. 启动应用（只能设置 path 与 launchMode）
client.sendHostAction(HostActionRequest.startApp()
        .path("C:\\Users\\kid\\AppData\\Local\\SourceTree\\app-3.4.30\\SourceTree.exe")
        .launchMode(HostActionRequest.HostLaunchMode.ACTIVATE_OR_START)  // 可选
        .build());

// 3. 打开浏览器（只能设置 url）
client.sendHostAction(HostActionRequest.openBrowser()
        .url("google.com")
        .build());

// 4. 输入文本（只能设置 text）
client.sendHostAction(HostActionRequest.inputText()
        .text("hello")
        .build());

// 5. 执行命令（只能设置 cmd）
client.sendHostAction(HostActionRequest.execCmd()
        .cmd("notepad")
        .build())
      .thenAccept(ack -> {
          if (!ack.success) {
              Log.e(TAG, "执行失败: " + ack.error.message);
          }
      });
```

**launchMode 启动方式**（`HostActionRequest.HostLaunchMode` 枚举）：

| 枚举值              | 线上值 | 含义                                         |
| ------------------- | ------ | -------------------------------------------- |
| `NEW_PROCESS`       | 0      | 总是启动一个新的进程                         |
| `ACTIVATE_ONLY`     | 1      | 只做激活（不启动新进程，仅激活已运行的进程） |
| `ACTIVATE_OR_START` | 2      | 未启动过进程则启动；已有启动的进程则激活     |

- 动作类型用 `HostAction` 枚举区分，服务端回调 `PluginServerListener.onHostAction`，通过 `request.action` 准确区分动作、`request.params` 取参数；
- 每个专属 Builder 的 `build()` 校验必填参数（如 `openFile()` 必填 `path`），缺失抛 `IllegalArgumentException`；新增动作时只扩展枚举与 Builder，**不需要新增发送方法**；
- 返回值中的 `success=false` 表示宿主执行失败。

---

## 8. 回调速查表

`PluginListener` 所有方法均为 `default` 空实现，按需 override：

| 回调                                                               | 触发时机                                          | 必须实现           |
| ------------------------------------------------------------------ | ------------------------------------------------- | ------------------ |
| `onRegisterSuccess(RegisterResultPayload)`                         | 注册成功（可拿 configServer.port / sessionToken） | 建议               |
| `onRegisterFailed(Throwable)`                                      | 注册失败                                          | 建议               |
| `onActionsAck(ActionsResultPayload)`                               | Action 配置同步 ACK                               | 可选               |
| `onActionsFailed(Throwable)`                                       | Action 上报失败                                   | 可选               |
| `onActionsUpdate(ActionsPayload)`                                  | LineOS 推送动作列表变更                           | 可选               |
| `onDescribe(DescribePayload, DescribeResponder)`                   | LineOS 询问 Action 参数定义                       | 有参数 Action 必须 |
| `onOptions(GetOptionsPayload, OptionsResponder)`                   | 拉取 REMOTE 类型的参数选项                        | 有 REMOTE 参数必须 |
| `onCall(CallPayload, CallResponder)`                               | 执行 Action                                       | **必须**           |
| `onModuleEvent(ModuleEventPayload, ModuleEventResponder)`          | 模块硬件事件                                      | 订阅了模块则必须   |
| `onA2UIActionCall(A2UIActionCallPayload, A2UIActionCallResponder)` | A2UI 用户交互回传（默认回 NO_HANDLER）            | 有交互组件则必须   |
| `onA2UIDataRequest()`                                              | LineOS 主动拉取 A2UI 数据（默认返回空列表）       | A2UI 插件建议      |
| `onA2UIRender(A2UIRenderPayload, A2UIRenderResponder)`             | 卡片首次渲染/刷新渲染通知（默认回 NO_HANDLER）    | A2UI 插件可选      |
| `onMessage(MessagePayload, MessageResponder)`                      | LineOS 下发消息（默认回 NO_HANDLER）              | 可选               |

**Responder 通用约定：**

- `responder.sendSuccess(data)` —— 成功，`data` 可为 null
- `responder.sendError(code, message, retryable)` —— 失败；`retryable=true` 提示LineOS 可重试

---

## 9. 注意事项与常见问题

### 9.1 构建与集成

1. **`packagingOptions` 必须排除 Netty 元文件**（见 2.1），否则打包冲突。
2. `meta-data` 的 `android:value` **必须等于 `applicationId`**，它是LineOS 识别插件的唯一标识，不一致将导致注册后会话对不上。
3. 服务必须 `android:exported="true"` 并带固定 permission，二者缺一不可。

### 9.2 运行时行为

4. **前台服务要求**：`onCreate()` 中必须在数秒内调用 `startForeground()`，否则系统 ANR/杀进程。
5. **回调线程非主线程**：`PluginListener` 所有回调运行在 SDK 线程池，UI 操作请自行 `Looper.getMainLooper()` 切换。
6. **断线自动重连**：SDK 自动重连并重新注册 + 重新上报 Actions；但 **A2UI 的 UI 数据不会自动重发**——LineOS 会在注册成功后主动拉取一次（通过 `onA2UIDataRequest`），所以实现好该回调即可保证重连后 UI 数据恢复。
7. **sessionToken 需自行校验**：它是调试会话凭证，不要在 HTTP 接口中无校验信任。
8. **热键 down/up 必须配对**：`sendHotkeyDown` 后忘记 `sendHotkeyUp` 会由 SDK 在超时后自动补发 up（默认 3000ms），但补发时序不受你控制；需要精确长按语义时请自行保证配对，见 [7.3](#73-长按-sendhotkeydown--sendhotkeyup)。

### 9.3 协议契约

8. **参数 id 一致性**：`onDescribe` 的 `ParameterSpec.id` 即 `onCall` 中 `params` 的 key，两端必须一致。
9. **A2UI 版本必须为 v0.9.1**：每条 envelope 带 `"version": "v0.9.1"`。
10. **`supportsA2UI=true` 是LineOS 反向调用的前提**：不声明时插件仍可主动上报数据，但LineOS 的 `callA2UIAction` / `queryA2UIData` 会被拒绝。
11. **检查上报结果的 ack**：`refreshA2UI` / `sendA2UIUpdateDataModel` 的返回值中 `success=false` 表示LineOS 处理失败，插件侧应有日志或重试策略。
12. **模块事件是串行单播，不回复等于放弃**：`onModuleEvent` 中既不 `consume()` 也不 `ignore()` 时，会白白占用 1 秒超时后才轮到下一个插件——不打算处理时请**显式 `ignore()`**。详细分发规则见 [6.2](#62-分发规则重点)。

### 9.4 常见问题（FAQ）

**Q：`onRegisterSuccess` 后为什么LineOS 看不到我的 Action？**
A：检查 `setActionsConfig` 是否在 `startAsync()` 之前调用；查看 `onActionsAck` 的 `receivedCount` 是否与预期一致。

**Q：开发阶段如何自测？**
A：开发期可在自己的 App 中手动 `startService()` 拉起 `BackgroundService` 验证流程；正式环境由 LineOS 扫描 Manifest 自动拉起。

---