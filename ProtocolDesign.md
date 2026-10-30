# Ultrabar Plugin Protocol 设计文档

> 本文档描述 `com.ultrabar` 插件协议的工作原理、服务端 / 客户端 API 与回调接口。
> 所有代码可在 `com.ultrabar.plugin.*`（客户端）与 `com.ultrabar.server.*`（服务端）找到。

---

## 1. 技术栈与协议概览

| 项 | 说明 |
|----|------|
| 传输 | TCP (Netty 4.1 NIO)，默认 `127.0.0.1:39001` |
| 编码 | 按行分隔的 JSON，每条消息一行，以 `\n` 结尾 |
| 序列化 | Jackson 2.15，枚举字段用 `@JsonValue` / `@JsonCreator` 映射 wire name |
| 心跳 | 服务端 5s 间隔 / 15s 超时；超时后 session 被清理 |
| 请求-响应 | 每条 Envelope 带 `requestId`；调用方发请求后 Future 挂起，收到同 `requestId` 的 Result 后完成 |

### 1.1 Envelope — 所有消息的最外层壳

```json
{
  "type":          "call",          // MessageType 的 wireName
  "requestId":     "req-xxx-yyy",   // 请求唯一标识；one-way 消息可以为 null
  "sessionId":     "sess-abc",       // 注册成功后服务端下发
  "sessionToken":  "tk-xyz",         // 同上
  "payload":       { ... }           // 具体业务载荷，按 type 反序列化
}
```

### 1.2 MessageType — 所有消息类型

| 枚举值 | wireName | 方向 | payload |
|--------|----------|------|---------|
| `REGISTER` | register | C→S | RegisterPayload |
| `REGISTER_RESULT` | register_result | S→C | RegisterResultPayload |
| `ACTIONS` | actions | C→S | ActionsPayload |
| `ACTIONS_RESULT` | actions_result | S→C | ActionsResultPayload |
| `ACTIONS_UPDATE` | actions_update | S→C | ActionsPayload |
| `DESCRIBE` | describe | S→C | DescribePayload |
| `DESCRIBE_RESULT` | describe_result | C→S | DescribeResultPayload |
| `CALL` | call | S→C | CallPayload |
| `CALL_RESULT` | call_result | C→S | CallResultPayload |
| `GET_OPTIONS` | get_options | S→C | GetOptionsPayload |
| `GET_OPTIONS_RESULT` | get_options_result | C→S | GetOptionsResultPayload |
| `HEARTBEAT` | heartbeat | C↔S | HeartbeatPayload |
| `HEARTBEAT_ACK` | heartbeat_ack | C↔S | HeartbeatAckPayload |
| `EVENT` | event | C→S | EventPayload |
| `EVENT_RESULT` | event_result | S→C | EventResultPayload |
| `MODULE_EVENT_REGISTER` | module_event_register | C→S | ModuleEventRegisterPayload |
| `MODULE_EVENT_UNREGISTER` | module_event_unregister | C→S | ModuleEventUnregisterPayload |
| `MODULE_EVENT` | module_event | S→C | ModuleEventPayload |
| `MODULE_EVENT_RESULT` | module_event_result | C→S | ModuleEventResultPayload |
| `AGENUI_DATA` | agenui_data | C→S | AGenUIDataPayload |
| `AGENUI_DATA_RESULT` | agenui_data_result | S→C | AGenUIDataResultPayload |
| `AGENUI_UPDATE_DATA_MODEL` | agenui_update_data_model | C→S | AGenUIUpdateDataModelPayload |
| `AGENUI_UPDATE_DATA_MODEL_RESULT` | agenui_update_data_model_result | S→C | AGenUIUpdateDataModelResultPayload |
| `AGENUI_ACTION_CALL` | agenui_action_call | S→C | AGenUIActionCallPayload |
| `AGENUI_ACTION_CALL_RESULT` | agenui_action_call_result | C→S | AGenUIActionCallResultPayload |

> **方向说明**：C→S = 客户端发给服务端；S→C = 服务端发给客户端；C↔S = 双向。

---

## 2. 连接建立与握手流程

```
客户端                                 服务端
  │  ─── TCP connect ──────────────► │
  │  ◄─── accept ──────────────────  │
  │  ─── REGISTER ─────────────────► │   (RegisterPayload)
  │                                  │   sessionId / sessionToken 生成, SessionRegistry 注册
  │  ◄─── REGISTER_RESULT ───────── │   (RegisterResultPayload, success=true)
  │                                  │
  │  ─── ACTIONS ──────────────────► │   (ActionsPayload)
  │                                  │   PluginSession 保存 actions
  │  ◄─── ACTIONS_RESULT ────────── │   (ActionsResultPayload)
  │                                  │
  │  ══════ 会话就绪，开始心跳 ══════  │
```

### 2.1 客户端侧关键类

| 类 | 职责 |
|----|------|
| `PluginClient` | 对外门面；组合下面所有组件 |
| `LineConnection` | Netty 连接管理（重连 / 断开 / 消息回调） |
| `EnvelopeClient` | 发请求（挂 Future 到 RequestTable）与 one-way 消息 |
| `RequestTable` | `requestId → CompletableFuture` 映射，超时自动失败 |
| `Handshake` | 驱动 REGISTER → ACTIONS 时序，设置 `onSessionReady` |
| `HeartbeatScheduler` | 定时发 HEARTBEAT，超时触发 `lineConnection.reconnect()` |
| `InboundDispatcher` | 收到消息后按 MessageType 分流 |
| `ListenerNotifier` | 把回调投递到独立线程池，避免阻塞 Netty EventLoop |
| `SessionState` | 客户端侧缓存的 sessionId / sessionToken |

### 2.2 服务端侧关键类

| 类 | 职责 |
|----|------|
| `PluginServer` | 对外门面；管理 SessionRegistry / 心跳定时 / 所有 request 分发 |
| `ServerChannelHandler` | Netty inbound handler，把每行 JSON 交给 PluginServer.dispatch() |
| `SessionRegistry` | `packageName → PluginSession`，替换会话时继承前后台状态 |
| `PluginSession` | 一个在线插件的全部状态（register info / actions / 前后台 / module 订阅 / AGenUI 缓存） |

### 2.3 客户端最小启动代码

```java
PluginClient client = new PluginClient("127.0.0.1", 39001);

RegisterPayload rp = new RegisterPayload();
rp.name = "Music";
rp.packageName = "com.ultrabar.music";
rp.supportsAGenUI = true;           // 第 4 点：声明支持 AGenUI
client.setRegisterConfig(rp);

client.setPluginListener(new PluginListener() {
    @Override
    public void onRegisterSuccess(RegisterResultPayload payload) {
        // 注册完成，开始做事
    }

    @Override
    public void onCall(CallPayload payload, CallResponder responder) {
        // 被服务端调用
    }
});

client.startAsync().get();            // 阻塞到连接 + 注册成功
```

### 2.4 服务端最小启动代码

```java
PluginServer server = new PluginServer(39001);
server.setRegisterHandler((RegisterPayload req) -> {
    RegisterResultPayload r = new RegisterResultPayload();
    r.success = true;
    r.configServer = new ConfigServer();
    r.configServer.port = 100;
    r.sessionToken = UUID.randomUUID().toString();
    return r;
});
server.setListener(new PluginServerListener() {
    @Override public void onRegistered(PluginSession s) { /* 插件上线 */ }
    @Override public void onUnregistered(PluginSession s) { /* 插件离线 */ }
});
server.start();
```

---

## 3. 核心能力一：服务端调用客户端 Action（Server → Client）

### 3.1 流程

```
服务端                                    客户端
  │  server.call(pkg, "music.play", {})  │
  │  ─── CALL ────────────────────────► │
  │                                     │  InboundDispatcher.handleCall()
  │                                     │  ListenerNotifier → PluginListener.onCall()
  │  ◄─── CALL_RESULT ───────────────── │  responder.sendSuccess({...})
  │  future.complete(result)            │
```

### 3.2 服务端 API

```java
CompletableFuture<CallResultPayload> call(String packageName, String actionId, Map<String, Object> params)
```

- 内部会校验该 actionId 是否被该插件注册（`session.hasFeatures(actionId, Features.CALL)`）
- 默认超时 30s，超时后 Future 以 `TimeoutException` 完成

### 3.3 客户端回调：PluginListener.onCall

```java
void onCall(CallPayload payload, CallResponder responder);
```

| 参数 | 说明 |
|------|------|
| `payload.actionId` | 被调用的 action id |
| `payload.params` | 调用参数 |
| `responder` | **必须**调用一次 `sendSuccess(result)` 或 `sendError(code, msg, retryable)` |

### 3.4 配套：服务端调 describe / getOptions

这两个是 `call` 的"前置准备"——服务端在调 action 前，可以先问客户端："这个 action 需要什么参数？参数有哪些可选值？"

```
DESCRIBE       → PluginListener.onDescribe(payload, DescribeResponder)
GET_OPTIONS    → PluginListener.onOptions(payload, OptionsResponder)
```

---

## 4. 核心能力二：客户端向服务端发事件（Client → Server）

### 4.1 Event（客户端自定义事件）

```java
CompletableFuture<EventResultPayload> sendEvent(String eventId, Map<String, Object> params)
```

- 服务端回调：`PluginServerListener.onEventReceived(session, event)`

### 4.2 AGenUI 数据上报

```java
CompletableFuture<AGenUIDataResultPayload> sendAGenUIData(String data)
CompletableFuture<AGenUIUpdateDataModelResultPayload> sendAGenUIUpdateDataModel(String dataModel)
CompletableFuture<AGenUIUpdateDataModelResultPayload> sendAGenUIUpdateDataModel(String dataModel, boolean incremental)
```

**服务端行为**：收到后
1. `session.setAGenUIData(data)` / `session.setAGenUIDataModel(dataModel)` — 存入 PluginSession
2. `listener.onAGenUIData(session, payload)` / `listener.onAGenUIUpdateDataModel(session, payload)` — 回调宿主
3. 回 ACK 给客户端

**服务端侧数据读取**（无需回调，随时可取）：
```java
PluginSession session = server.getSession("com.ultrabar.music");
String audioData = session.aGenUIData();          // 最近一次 data JSON 字符串
String state     = session.aGenUIDataModel();     // 最近一次 dataModel JSON 字符串
```

**客户端注册时声明支持 AGenUI**：
```java
RegisterPayload rp = new RegisterPayload();
rp.supportsAGenUI = true;   // 第 4 点
```

> 如果客户端没声明 `supportsAGenUI=true`，服务端 `callAGenUIAction` 会立即拒绝；但客户端仍可上报 data（上报通道本身不强校验）。

---

## 5. 核心能力三：服务端向客户端发起 AGenUI Action Call（Server → Client）

### 5.1 流程

```
服务端                                         客户端
  │  server.callAGenUIAction(pkg, "chat", argsJson) │
  │  ① 校验 session.supportsAGenUI()          │
  │  ─── AGENUI_ACTION_CALL ───────────────► │
  │                                           │  InboundDispatcher.handleAGenUIActionCall()
  │                                           │  ListenerNotifier → PluginListener.onAGenUIActionCall()
  │  ◄─── AGENUI_ACTION_CALL_RESULT ───────── │  responder.sendSuccess(...) / sendError(...)
  │  future.complete(result)                  │
```

### 5.2 服务端 API

```java
CompletableFuture<AGenUIActionCallResultPayload> callAGenUIAction(
        String packageName, String functionName, String args)
```

- `args` 为 JSON 字符串（可为 null），例如 `"{\"prompt\":\"hello\"}"`
- 若 session 不存在 → Future 立即失败 `"no active session"`
- 若 session 未声明 `supportsAGenUI=true` → Future 立即失败 `"does not support agenui"`
- 若 `functionName` 为空 → Future 立即失败

### 5.3 客户端回调：PluginListener.onAGenUIActionCall

```java
default void onAGenUIActionCall(AGenUIActionCallPayload payload, AGenUIActionCallResponder responder) {
    responder.sendError(ErrorCodes.NO_HANDLER, "no agenui action handler registered", false);
}
```

**注意**：`PluginListener` 里只有 `onAGenUIActionCall` 是 default 实现（返回 NO_HANDLER 错误），其他 `onCall` / `onRegisterSuccess` 是 abstract，必须实现。

| Responder 方法 | 说明 |
|----------------|------|
| `sendSuccess(Object result)` | 调用成功，可返回任意对象 |
| `sendError(String code, String msg, boolean retryable)` | 调用失败 |

---

## 6. 核心能力四：模块事件分发（Multi-Plugin Multicast）

模块事件是"一个插件发、多个插件收"的场景，典型如播放器事件、智能家居事件。

### 6.1 订阅 / 取消订阅

```java
// 客户端：声明关心哪些模块
client.subscribeModuleEvents(ModuleType.PLAYER, ModuleType.SMART_HOME);  // 一次传多个
client.unsubscribeModuleEvents(ModuleType.SMART_HOME);
// 重连后 Handshake.onSessionReady 会自动重发 subscribe
```

### 6.2 服务端分发

```java
server.sendModuleEvent(ModuleType.PLAYER, "player_01", "play", eventObj)
      .thenAccept(result -> {
          result.handled()       // 有插件消费了
          result.fallback()      // 全部未消费，走了兜底
          result.packageName()   // 消费该事件的插件包名
      });
```

### 6.3 分发策略

1. 服务端从已订阅该 `ModuleType` 的插件中找候选
2. **前台优先**：按 `foregroundAtMillis` 倒序；后台按 `lastSeenMillis` 倒序
3. 逐个候选发 `MODULE_EVENT`，等 `MODULE_EVENT_RESULT` 或超时（默认 1s）
4. 有插件 `consume()` → 结束；全部忽略 → `invokeModuleEventFallback()`

### 6.4 客户端回调：PluginListener.onModuleEvent

```java
default void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
    responder.ignore("no handler");
}
```

| Responder 方法 | 说明 |
|----------------|------|
| `consume()` | 本插件消费了，服务端停止继续分发 |
| `ignore(String reason)` | 本插件不处理，服务端继续找下一个 |

> **关键**：两个方法只能调一次。如果客户端忘了调，服务端会等 1s 超时再继续。

### 6.5 兜底回调（未被任何插件消费时）

**方式一：注册式兜底（优先）**

```java
// 同一 ModuleType 可注册多个，全部触发
server.registerFallback(ModuleType.PLAYER, event -> {
    // 兜底处理
});
server.registerFallback(ModuleType.PLAYER, event -> {
    // 再来一个兜底
});

server.unregisterFallback(ModuleType.PLAYER, handler);      // 解绑单个
server.removeAllFallbacks(ModuleType.PLAYER);               // 一键清空
```

**方式二：PluginServerListener.onModuleEventFallback（默认）**

```java
server.setListener(new PluginServerListener() {
    @Override
    public void onModuleEventFallback(ModuleEventPayload event) {
        // 只有该 ModuleType 没注册任何 registerFallback 时才走到这里
    }
});
```

**优先级**：`registerFallback` 注册了 → 走注册式，不走 listener；没注册 → 走 listener。

### 6.6 客户端收到模块事件时的 appState

服务端给每个候选插件发事件时，会把 `payload.appState` 设为 `"foreground"` / `"background"` / `"none"`（兜底时），让客户端知道自己当时的前后台状态。

### 6.7 服务端手动设置前后台

```java
server.setAppState("com.ultrabar.music", true);          // 单独设某个插件
server.setForegroundApp("com.ultrabar.music");           // 该插件前台，其余后台
```

---

## 7. PluginSession — 服务端维护的插件状态快照

| 方法 | 说明 |
|------|------|
| `packageName()` | 注册包名 |
| `name()` | 注册名 |
| `supportsAGenUI()` | 注册时是否声明支持 AGenUI |
| `actions()` | 当前注册的 ActionSummary 集合 |
| `moduleEvents()` | 当前订阅的 ModuleType 集合 |
| `handlesModuleEvent(ModuleType)` | 是否订阅了该模块 |
| `isForeground()` / `foregroundAtMillis()` | 前后台状态与切换时间 |
| `aGenUIData()` | 最近一次 AGenUI 数据 JSON 字符串 |
| `aGenUIDataModel()` | 最近一次 AGenUI dataModel JSON 字符串 |
| `lastSeenMillis()` | 最后一次通信时间 |

---

## 8. 回调接口汇总

### 8.1 客户端：PluginListener

| 方法 | 方向 | 必选 | Responder | 说明 |
|------|------|------|-----------|------|
| `onRegisterSuccess(RegisterResultPayload)` | S→C | ✅ | — | 注册成功 |
| `onRegisterFailed(Throwable)` | — | ❌ | — | 注册失败 |
| `onActionsAck(ActionsResultPayload)` | S→C | ❌ | — | Actions 配置 ACK |
| `onActionsUpdate(ActionsPayload)` | S→C | ❌ | — | 服务端下发 Actions 变更 |
| `onDescribe(DescribePayload, DescribeResponder)` | S→C | ❌ | DescribeResponder | 参数描述查询 |
| `onCall(CallPayload, CallResponder)` | S→C | ✅ | CallResponder | 被服务端调用 |
| `onModuleEvent(ModuleEventPayload, ModuleEventResponder)` | S→C | ❌ | ModuleEventResponder | 模块事件 |
| `onAGenUIActionCall(AGenUIActionCallPayload, AGenUIActionCallResponder)` | S→C | ❌ | AGenUIActionCallResponder | AGenUI action call（默认返回 NO_HANDLER） |
| `onOptions(GetOptionsPayload, OptionsResponder)` | S→C | ❌ | OptionsResponder | 动态参数选项查询 |

### 8.2 服务端：PluginServerListener

| 方法 | 方向 | 默认实现 | 说明 |
|------|------|----------|------|
| `onRegistered(PluginSession)` | 事件 | 空 | 插件注册上线 |
| `onUnregistered(PluginSession)` | 事件 | 空 | 插件离线 |
| `onActionsUpdated(PluginSession)` | 事件 | 空 | Actions 配置更新 |
| `onEventReceived(PluginSession, EventPayload)` | C→S | 空 | 客户端发 Event |
| `onModuleEventFallback(ModuleEventPayload)` | 兜底 | 空 | 模块事件全部未消费时兜底 |
| `onAGenUIData(PluginSession, AGenUIDataPayload)` | C→S | 空 | 客户端上报 AGenUI 数据（已存入 session） |
| `onAGenUIUpdateDataModel(PluginSession, AGenUIUpdateDataModelPayload)` | C→S | 空 | 客户端上报 AGenUI dataModel（已存入 session） |

### 8.3 服务端：registerFallback（注册式兜底，独立于 PluginServerListener）

```java
server.registerFallback(ModuleType module, ModuleEventFallbackHandler handler);
server.unregisterFallback(ModuleType module, ModuleEventFallbackHandler handler);
server.removeAllFallbacks(ModuleType module);
```

---

## 9. 服务端 API 汇总

| 方法 | 说明 |
|------|------|
| `PluginServer(int port)` | 指定端口，0 = OS 动态分配 |
| `start()` / `stop()` | 生命周期 |
| `getPort()` | start 后返回实际绑定端口 |
| `sessions()` | 所有在线 PluginSession 集合 |
| `getSession(String packageName)` | 按包名查 session |
| `setRegisterHandler(PluginRegisterHandler)` | 自定义注册逻辑 |
| `setListener(PluginServerListener)` | 设置全局监听器 |
| `call(String pkg, String actionId, Map params)` | 调用插件 Action |
| `callAGenUIAction(String pkg, String functionName, String args)` | 调用插件 AGenUI Action |
| `setAppState(String pkg, boolean foreground)` | 设置插件前后台 |
| `setForegroundApp(String pkg)` | 该插件前台，其余后台 |
| `sendModuleEvent(ModuleType, String moduleId, String action, Object event)` | 触发模块事件 |
| `sendModuleEvent(..., long timeoutMs)` | 同上，自定义单候选等待时间 |
| `setModuleEventTimeoutMs(long)` | 修改默认 1s 超时 |
| `registerFallback(ModuleType, ModuleEventFallbackHandler)` | 注册式兜底 |
| `unregisterFallback(ModuleType, ModuleEventFallbackHandler)` | 解绑 |
| `removeAllFallbacks(ModuleType)` | 一键清空该模块兜底 |

---

## 10. 客户端 API 汇总

| 方法 | 说明 |
|------|------|
| `PluginClient(String host, int port)` | 指定服务端地址 |
| `setRegisterConfig(RegisterPayload)` | 注册信息（含 `supportsAGenUI`） |
| `setActionsConfig(ActionsPayload)` | 声明支持的 Actions |
| `updateActions(ActionsPayload)` | 运行时更新 Actions |
| `setPluginListener(PluginListener)` | 设置回调 |
| `startAsync()` / `stop()` | 生命周期 |
| `call(...)` | 服务端主动调我（被动，非主动发） |
| `sendEvent(String eventId, Map params)` | 发 Event |
| `sendAGenUIData(String data)` | 上报 AGenUI 数据 JSON 字符串 |
| `sendAGenUIUpdateDataModel(String dataModel)` | 上报 dataModel JSON 字符串 |
| `sendAGenUIUpdateDataModel(String dataModel, boolean incremental)` | 增量上报 |
| `subscribeModuleEvents(ModuleType... modules)` | 订阅模块事件（可一次多个） |
| `unsubscribeModuleEvents(ModuleType... modules)` | 取消订阅（可一次多个） |

---

## 11. RegisterPayload 完整字段

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `name` | String | — | 插件名 |
| `packageName` | String | — | 包名（服务端以此为 session key） |
| `isConfig` | boolean | true | 是否有配置页面 |
| `configBtnName` | String | — | 配置按钮名 |
| **`supportsAGenUI`** | **boolean** | **false** | **是否声明支持 AGenUI（影响服务端 callAGenUIAction 校验）** |

---

## 12. 典型时序图

### 12.1 完整注册 → 调用 → AGenUI 上报 → 兜底

```
PluginClient         PluginServer          PluginServerListener
     │                    │                         │
     ├── startAsync() ──►│                         │
     │                    ├── REGISTER ────────────►│  (PluginRegisterHandler)
     │◄── REGISTER_RESULT │                         │
     │                    │                         ├── onRegistered()
     ├── ACTIONS ────────►│                         │
     │◄── ACTIONS_RESULT  │                         │
     │                    │                         ├── onActionsUpdated()
     │  ═══ 会话就绪 ════ │                         │
     │                    │                         │
     │                    │  (宿主想调用插件 action) │
     │◄── CALL ───────────│                         │
     ├── responder.sendSuccess() ─────────────────►│
     │                    │                         │
     │                    │  (宿主想发模块事件)       │
     │◄── MODULE_EVENT ──►│                         │
     ├── responder.consume() ──────────────────────►│
     │                    │                         │
     │                    │  (宿主想发 AGenUI action)│
     │◄── AGENUI_ACTION_CALL ──────────────────────►│
     ├── responder.sendSuccess() ──────────────────►│
     │                    │                         │
     │  (插件上报 AGenUI)  │                         │
     ├── AGENUI_DATA ────►│                         │
     │◄── AGENUI_DATA_RESULT │                      ├── onAGenUIData()
     │                    │  (数据已存入 session)    │
     │                    │                         │
     │  (所有插件都没消费)  │                         │
     │                    │  invokeModuleEventFallback()
     │                    │                         ├── registerFallback 注册的回调
     │                    │                         │ (若无注册式兜底)
     │                    │                         └── onModuleEventFallback()
```

---

## 13. 编译与测试

```powershell
.\gradlew.bat compileJava              # 主源码
.\gradlew.bat compileTestJava          # 测试源码
.\gradlew.bat test                     # 跑全部测试
.\gradlew.bat build                    # 完整构建（含 jar）
```

当前测试覆盖：

| 测试类 | 内容 |
|--------|------|
| `AGenUIProtocolTest` | AGenUI payload 构造 / JSON 序列化 / MessageType 绑定 |
| `PluginSessionAGenUITest` | PluginSession 的 AGenUI 存储方法 |
| `AGenUIIntegrationTest` | 端到端：起真实 Netty server + client，覆盖上报 / 回调 / action call / supportsAGenUI 校验 / 默认 NO_HANDLER |

---

## 14. 服务端完整代码示例

> 下面这段是一个典型宿主 App 的完整骨架：启动 server、自定义注册校验、管理前后台、调用插件 action、分发模块事件、触发 AGenUI action call，并从 PluginSession 里读取 AGenUI 数据快照。可直接放在 `main()` 里运行。

```java
package com.example.host;

import com.ultrabar.plugin.model.*;
import com.ultrabar.server.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HostApp {

    public static void main(String[] args) throws Exception {

        // ──────────────────────────────
        // 1. 启动服务端（port=0 让 OS 动态分配）
        // ──────────────────────────────
        PluginServer server = new PluginServer("127.0.0.1", 0);  // start() 后用 server.getPort() 取实际端口

        // ──────────────────────────────
        // 2. 自定义注册校验（可选；默认实现会放行一切 packageName）
        //    可以在这里做签名校验、白名单、配额等
        // ──────────────────────────────
        server.setRegisterHandler((RegisterPayload request) -> {
            RegisterResultPayload r = new RegisterResultPayload();
            // 拒绝空包名
            if (request.packageName == null || request.packageName.trim().isEmpty()) {
                r.success = false;
                r.error = ErrorInfo.of(ErrorCodes.MISSING_PACKAGE, "packageName 不能为空", false, null);
                return r;
            }
            r.success = true;
            r.configServer = new ConfigServer();
            r.configServer.port = 100 + new Random().nextInt(50);  // 给每个插件分配一个调试端口
            r.sessionToken = UUID.randomUUID().toString();
            return r;
        });

        // ──────────────────────────────
        // 3. 全局监听器
        // ──────────────────────────────
        server.setListener(new PluginServerListener() {

            @Override
            public void onRegistered(PluginSession session) {
                System.out.println("[host] plugin online name=" + session.name()
                        + " pkg=" + session.packageName()
                        + " supportsAGenUI=" + session.supportsAGenUI());
                // 第一个上线的插件默认前台；后续可按应用状态手动调整
                server.setForegroundApp(session.packageName());
            }

            @Override
            public void onUnregistered(PluginSession session) {
                System.out.println("[host] plugin offline pkg=" + session.packageName());
            }

            @Override
            public void onActionsUpdated(PluginSession session) {
                System.out.println("[host] actions updated pkg=" + session.packageName()
                        + " count=" + session.actions().size());
            }

            @Override
            public void onEventReceived(PluginSession session, EventPayload event) {
                System.out.println("[host] event from " + session.packageName()
                        + " eventId=" + event.eventId + " params=" + event.params);
            }

            // —— AGenUI 数据回调 —— //
            @Override
            public void onAGenUIData(PluginSession session, AGenUIDataPayload payload) {
                // payload 此刻已存入 session.aGenUIData()；这里做业务分发即可
                System.out.println("[host] AGenUI data from " + session.packageName()
                        + " data=" + payload.data);
            }

            @Override
            public void onAGenUIUpdateDataModel(PluginSession session, AGenUIUpdateDataModelPayload payload) {
                System.out.println("[host] AGenUI dataModel from " + session.packageName()
                        + " incremental=" + payload.incremental
                        + " model=" + payload.dataModel);
            }

            // —— 模块事件兜底（仅当该 ModuleType 未注册 registerFallback 时才会走到这里）—— //
            @Override
            public void onModuleEventFallback(ModuleEventPayload event) {
                System.out.println("[host] fallback (listener) module=" + event.module
                        + " action=" + event.action);
            }
        });

        // ──────────────────────────────
        // 4. 注册式模块事件兜底（优先级高于 onModuleEventFallback）
        //    同一 ModuleType 可注册多个；兜底时全部执行
        // ──────────────────────────────
        ModuleEventFallbackHandler playerFallback = event ->
                System.out.println("[host] fallback (registered) module=" + event.module
                        + " action=" + event.action);
        server.registerFallback(ModuleType.PLAYER, playerFallback);
        server.registerFallback(ModuleType.PLAYER, event ->
                System.out.println("[host] second fallback for PLAYER"));
        // 解绑单个 / 一键清空
        // server.unregisterFallback(ModuleType.PLAYER, playerFallback);
        // server.removeAllFallbacks(ModuleType.PLAYER);

        // ──────────────────────────────
        // 5. 启动服务端
        // ──────────────────────────────
        server.start();
        int port = server.getPort();
        System.out.println("[host] listening on 127.0.0.1:" + port);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        // ──────────────────────────────
        // 6. 宿主主动调用插件 action（示例动作）
        // ──────────────────────────────
        //   · 服务端会校验目标插件是否注册了该 actionId
        //   · 默认 30s 超时，超时后 future 以 TimeoutException 完成
        CompletableFuture<CallResultPayload> callFut = server.call(
                "com.ultrabar.music",     // 目标插件 packageName
                "music.play",             // actionId
                Map.of("deviceId", "sp00-1")  // 参数（Key 必须与插件 onDescribe 里的 ParameterSpec.id 一致）
        );
        callFut.whenComplete((result, err) -> {
            if (err != null) {
                System.out.println("[host] call failed: " + err.getMessage());
            } else {
                System.out.println("[host] call result success=" + result.success
                        + " data=" + result.data);
            }
        });

        // ──────────────────────────────
        // 7. 调用插件的 AGenUI action call
        //    · session 不存在 → Future 立即失败 "no active session"
        //    · session.supportsAGenUI() == false → Future 立即失败 "does not support agenui"
        //    · functionName 为空 → Future 立即失败
        // ──────────────────────────────
        String argsJson = Json.mapper().writeValueAsString(Map.of("question", "现在播放的是什么歌？"));
        CompletableFuture<AGenUIActionCallResultPayload> agenuiFut =
                server.callAGenUIAction("com.ultrabar.music", "chat", argsJson);
        agenuiFut.whenComplete((result, err) -> {
            if (err != null) {
                System.out.println("[host] AGenUI action failed: " + err.getMessage());
            } else {
                System.out.println("[host] AGenUI action result success=" + result.success);
            }
        });

        // ──────────────────────────────
        // 8. 分发模块事件（前台优先、后台其次；全部未消费走兜底）
        // ──────────────────────────────
        Map<String, Object> event = new HashMap<>();
        event.put("key", "play");
        server.sendModuleEvent(ModuleType.DOT_KEY, "player_01", "play", event)
              .thenAccept(result -> {
                  System.out.println("[host] module event handled=" + result.handled()
                          + " fallback=" + result.fallback()
                          + " pkg=" + result.packageName()
                          + " appState=" + result.event().appState);
              });

        // 自定义超时：等单个插件 5s 才视为超时
        // server.sendModuleEvent(ModuleType.DOT_KEY, "player_01", "play", event, 5000L);

        // ──────────────────────────────
        // 9. 手动调整前后台（模块事件分发时前台优先）
        // ──────────────────────────────
        server.setForegroundApp("com.ultrabar.music");   // 该插件前台，其余全部后台
        server.setAppState("com.ultrabar.smart_home", false);  // 单独把某个插件置为后台

        // ──────────────────────────────
        // 10. 直接从 PluginSession 读取 AGenUI 数据（无需回调）
        // ──────────────────────────────
        PluginSession musicSession = server.getSession("com.ultrabar.music");
        if (musicSession != null) {
            String audioEvent = musicSession.aGenUIData();
            String agentStateJson = musicSession.aGenUIDataModel();
            System.out.println("[host] cached audioEvent=" + audioEvent + " state=" + agentStateJson);
        }

        // ──────────────────────────────
        // 11. 列出所有在线插件
        // ──────────────────────────────
        for (PluginSession s : server.sessions()) {
            System.out.println("[host] online: " + s.packageName()
                    + " actions=" + s.actions().size()
                    + " modules=" + s.moduleEvents()
                    + " foreground=" + s.isForeground());
        }

        // 保持主线程活着，等待插件接入
        Thread.currentThread().join();
    }
}
```

---

## 15. 客户端完整代码示例

> 下面是一个典型音乐插件的完整骨架：注册支持 AGenUI、声明 play/pause/stop 三个 action、订阅 DOT_KEY 模块事件、实现 `onCall` / `onModuleEvent` / `onAGenUIActionCall`、主动上报 AGenUI 数据。可直接作为插件的入口类。

```java
package com.ultrabar.music;

import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.*;
import com.ultrabar.plugin.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MusicPlugin {

    public static void main(String[] args) throws Exception {

        // ──────────────────────────────
        // 1. 创建客户端并配置注册信息
        // ──────────────────────────────
        PluginClient client = new PluginClient("127.0.0.1", 39001);

        RegisterPayload rp = new RegisterPayload();
        rp.name = "Music";
        rp.packageName = "com.ultrabar.music";
        rp.isConfig = true;
        rp.configBtnName = "调试 Music";
        rp.supportsAGenUI = true;   // ⚠️ 不写这行，服务端 callAGenUIAction 会立即拒绝

        // ──────────────────────────────
        // 2. 声明支持的 Actions
        //    Features.CALL = 我能被服务端主动调用（onCall）
        //    Features.EVENT = 我能主动向服务端发 Event（sendEvent）
        // ──────────────────────────────
        ActionSummary play = new ActionSummary();
        play.actionId = "music.play";
        play.name = "play music";
        play.description = "play on a device";
        play.features = new HashSet<>(Arrays.asList(Features.CALL, Features.EVENT));

        ActionSummary pause = new ActionSummary();
        pause.actionId = "music.pause";
        pause.name = "pause music";
        pause.description = "pause a device";
        pause.features = new HashSet<>(Collections.singletonList(Features.CALL));

        ActionSummary stop = new ActionSummary();
        stop.actionId = "music.stop";
        stop.name = "stop all play";
        stop.description = "stop all devices";
        stop.features = new HashSet<>(Collections.singletonList(Features.CALL));

        ActionsPayload ap = new ActionsPayload(Arrays.asList(play, pause, stop));
        // topic 字段保留（可能用于未来订阅类场景），当前 publish 流程已移除
        ap.topic = new HashSet<>();
        ap.topic.add(Topic.CPU);

        client.setRegisterConfig(rp);
        client.setActionsConfig(ap);

        // ──────────────────────────────
        // 3. 一次性订阅多个模块事件
        //    连接重连后 SDK 会自动重新发送订阅
        // ──────────────────────────────
        client.subscribeModuleEvents(
                ModuleType.DOT_KEY,
                ModuleType.KNOB_KEY,
                ModuleType.PLAYER
        );

        // ──────────────────────────────
        // 4. 设置回调（PluginListener）
        // ──────────────────────────────
        client.setPluginListener(new PluginListener() {

            // ==== 注册成功 / 失败 ====
            @Override
            public void onRegisterSuccess(RegisterResultPayload payload) {
                System.out.println("[music] register success sessionId=" + payload.sessionId
                        + " token=" + payload.sessionToken
                        + " port=" + payload.configServer.port);
                // 可以在这里启动自己的 HTTP 调试服务，绑定到 payload.configServer.port
            }

            @Override
            public void onRegisterFailed(Throwable t) {
                System.err.println("[music] register failed: " + t.getMessage());
                // 注册失败后不要启动依赖主 App 的 HTTP / Action 服务
            }

            @Override
            public void onActionsAck(ActionsResultPayload ack) {
                System.out.println("[music] actions ack success=" + ack.success
                        + " received=" + ack.receivedCount);
            }

            @Override
            public void onActionsUpdate(ActionsPayload update) {
                // 服务端主动推送的 Actions 变更（常规业务里很少用到）
                int count = (update != null && update.actions != null) ? update.actions.size() : 0;
                System.out.println("[music] actions update pushed count=" + count);
            }

            // ==== 被服务端调用 ====
            @Override
            public void onCall(CallPayload payload, CallResponder responder) {
                String actionId = payload.actionId;
                Map<String, Object> params = payload.params;
                System.out.println("[music] onCall actionId=" + actionId + " params=" + params);

                try {
                    switch (actionId) {
                        case "music.play": {
                            // params 的 Key 必须与 onDescribe() 里 ParameterSpec.id 一致
                            String deviceId = (String) params.get("deviceId");
                            doPlay(deviceId);
                            break;
                        }
                        case "music.pause": {
                            String deviceId = (String) params.get("deviceId");
                            doPause(deviceId);
                            break;
                        }
                        case "music.stop": {
                            doStop();
                            break;
                        }
                        default: {
                            responder.sendError(ErrorCodes.NO_HANDLER,
                                    "unknown action: " + actionId, false);
                            return;
                        }
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "ok");
                    result.put("actionId", actionId);
                    responder.sendSuccess(result);

                } catch (Exception e) {
                    // retryable=true 表示服务端可以在稍后重试一次
                    responder.sendError(ErrorCodes.INTERNAL, e.getMessage(), false);
                }
            }

            // ==== Action 参数描述（服务端调用前先问） ====
            @Override
            public void onDescribe(DescribePayload payload, DescribeResponder responder) {
                DescribeResultPayload result = new DescribeResultPayload();
                result.actionId = payload.actionId;
                result.success = true;

                if ("music.play".equals(payload.actionId)) {
                    DescribeResultPayload.ParameterSpec device =
                            new DescribeResultPayload.ParameterSpec();
                    device.id = "deviceId";
                    device.name = "设备";
                    device.required = true;
                    device.type = ParameterType.SELECT;
                    device.placeholder = "选择播放设备";
                    DescribeResultPayload.OptionSpec options =
                            new DescribeResultPayload.OptionSpec();
                    options.provider = OptionProvider.REMOTE;  // 主 App 会再调 onOptions 动态取
                    options.searchable = true;
                    device.options = options;
                    result.parameters = Collections.singletonList(device);
                }
                responder.sendSuccess(result);
            }

            // ==== 动态参数选项（REMOTE provider 时才会被调用） ====
            @Override
            public void onOptions(GetOptionsPayload payload, OptionsResponder responder) {
                GetOptionsResultPayload result = new GetOptionsResultPayload();
                result.success = true;
                result.hasMore = false;
                result.items = Arrays.asList(
                        new Label("Sony TV", "sp00-1"),
                        new Label("Sony Amp", "sp00-2")
                );
                responder.sendSuccess(result);
            }

            // ==== 模块事件 ====
            @Override
            public void onModuleEvent(ModuleEventPayload payload, ModuleEventResponder responder) {
                System.out.println("[music] module event module=" + payload.module
                        + " action=" + payload.action
                        + " appState=" + payload.appState   // foreground / background / none
                        + " event=" + payload.event);

                if (payload.module == ModuleType.DOT_KEY) {
                    // 我能处理这个模块 → consume() 后服务端不再继续分发
                    responder.consume();
                } else {
                    // 不处理 → 服务端继续尝试下一个插件 / 最终走兜底
                    responder.ignore("unsupported module: " + payload.module);
                }
            }

            // ==== AGenUI action call（默认返回 NO_HANDLER，这里 override 真正实现） ====
            @Override
            public void onAGenUIActionCall(AGenUIActionCallPayload payload,
                                            AGenUIActionCallResponder responder) {
                String functionName = payload.functionName;
                String argsJson = payload.args;
                System.out.println("[music] AGenUI function=" + functionName + " args=" + argsJson);

                switch (functionName) {
                    case "chat": {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = Json.mapper().readValue(argsJson, Map.class);
                            String question = (String) args.get("question");
                            Map<String, Object> reply = new HashMap<>();
                            reply.put("answer", "当前正在播放：周杰伦 - 晴天");
                            reply.put("question", question);
                            responder.sendSuccess(reply);
                        } catch (Exception e) {
                            responder.sendError(ErrorCodes.INTERNAL, "args json invalid: " + e.getMessage(), false);
                        }
                        break;
                    }
                    default: {
                        // 对于自己不认识的 AGenUI action，沿用默认 NO_HANDLER 行为
                        responder.sendError(ErrorCodes.NO_HANDLER,
                                "no handler for agenui function: " + functionName, false);
                    }
                }
            }
        });

        // ──────────────────────────────
        // 5. 启动（阻塞到握手完成）
        // ──────────────────────────────
        client.startAsync().get();

        // ──────────────────────────────
        // 6. 主动发 Event（需要该 eventId 先注册 Features.EVENT）
        // ──────────────────────────────
        Map<String, Object> eventParams = new HashMap<>();
        eventParams.put("trackName", "晴天");
        eventParams.put("artist", "周杰伦");
        client.sendEvent("music.play", eventParams)
              .whenComplete((result, err) -> {
                  if (err != null) {
                      System.err.println("[music] sendEvent failed: " + err.getMessage());
                  } else {
                      System.out.println("[music] sendEvent ack success=" + result.success);
                  }
              });

        // ──────────────────────────────
        // 7. 主动上报 AGenUI 数据（无论 supportsAGenUI=true/false 都可以上报）
        // ──────────────────────────────

        // 7a. 完整上报 —— 音频事件流（JSON 字符串）
        String audioEventJson = Json.mapper().writeValueAsString(Map.of(
                "type", "beat",
                "timestamp", System.currentTimeMillis()));
        client.sendAGenUIData(audioEventJson);

        // 7b. 完整 dataModel（JSON 字符串）
        String agentStateJson = Json.mapper().writeValueAsString(Map.of(
                "mode", "chat", "lastUserMessage", "你好"));
        client.sendAGenUIUpdateDataModel(agentStateJson);

        // 7c. 增量 dataModel（只传变化的字段，JSON 字符串）
        String agentDeltaJson = Json.mapper().writeValueAsString(Map.of("mode", "listen"));
        client.sendAGenUIUpdateDataModel(agentDeltaJson, true);  // incremental=true

        // ──────────────────────────────
        // 8. 订阅运行时变更 / 取消订阅
        // ──────────────────────────────
        client.subscribeModuleEvents(ModuleType.SMART_HOME);   // 新增一个
        client.unsubscribeModuleEvents(ModuleType.KNOB_KEY);   // 移除一个

        // 保持线程
        Thread.currentThread().join();
        // 退出时调用 client.stop();
    }

    private static void doPlay(String deviceId) {
        System.out.println("[music] doPlay on device=" + deviceId);
    }

    private static void doPause(String deviceId) {
        System.out.println("[music] doPause on device=" + deviceId);
    }

    private static void doStop() {
        System.out.println("[music] doStop");
    }
}
```

### 关键提醒（客户端示例注释外的额外说明）

| 项 | 说明 |
|----|------|
| `supportsAGenUI = true` | **必须**在注册时声明，否则服务端 `callAGenUIAction()` 立即失败。但客户端仍可上报 AGenUI 数据（上报通道不强校验） |
| `onCall` / `onModuleEvent` / `onAGenUIActionCall` 的 Responder | **务必且只能调用一次**。忘了调 → 服务端超时；调两次 → 第二次会被底层 `AtomicBoolean` 拒绝 |
| `responder.sendError(code, msg, retryable)` | `retryable=true` 告诉服务端这个错误可能是临时的，可以稍后重试 |
| `subscribeModuleEvents` | 可以在 `startAsync()` 前调用（先记录在本地），也可以在会话建立后调用（立即发送）。重连后 SDK 会自动重发 |
| `Features.CALL` vs `Features.EVENT` | `CALL` 声明"我能被服务端主动调"；`EVENT` 声明"我能主动向服务端发 Event"。服务端收到 Event 时会校验该 eventId 是否注册了 Features.EVENT |
| `sendAGenUIUpdateDataModel(String json, boolean incremental)` | `incremental=true` 表示该 JSON 只包含变化字段；服务端仍按最近一次字符串直接覆盖（不再按 modelType 分类存，也不做增量合并） |
