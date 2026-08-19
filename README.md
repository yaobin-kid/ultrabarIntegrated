# Ultrabar Integrated - Plugin SDK

这是基于 Netty 的 Ultrabar 插件协议 Java SDK 示例工程。

功能亮点：
- 使用 Netty 连接主 App（默认 127.0.0.1:39001，可通过构造函数修改）
- 提供 register、actions（上报）、describe、call 等接口
- 支持注册与 actions 的回调、actions_update 与 incoming `call` 的回调
- 使用 Jackson 作为 JSON 序列化/反序列化

快速开始

1) 克隆仓库并构建

```bash
git clone https://github.com/yaobin-kid/ultrabarIntegrated.git
cd ultrabarIntegrated
./gradlew jar
```

生成的 jar 在 `build/libs` 下，例如：

```bash
build/libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar
```

2) 必要依赖（Gradle）

在你的项目的 build.gradle 中添加：

```groovy
implementation 'io.netty:netty-all:4.1.94.Final'
implementation 'com.fasterxml.jackson.core:jackson-databind:2.15.2'
implementation 'org.slf4j:slf4j-simple:2.0.7'
```

（或使用 Maven 对应的 artifact）

3) 运行示例（本地测试）

> 注意：示例依赖一个能够接收协议消息的服务端（默认 127.0.0.1:39001）。服务端需以换行符 `\n` 作为消息结束（Line-based framing）。

```bash
java -jar build/libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar
```

4) 在你的项目中集成 SDK（示例代码）

下面示例展示了如何：
- 使用默认地址创建 PluginClient
- 注册插件并处理 register_result 的回调
- 上报 actions
- 查询 describe
- 调用 call
- 处理来自主 App 的 incoming `call`（CallHandler）

```java
import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.RegisterCallback;
import com.ultrabar.plugin.callback.ActionsCallback;
import com.ultrabar.plugin.callback.ActionsUpdateCallback;
import com.ultrabar.plugin.callback.CallHandler;
import com.ultrabar.plugin.model.RegisterPayload;
import com.ultrabar.plugin.model.PluginInfo;
import com.ultrabar.plugin.model.ActionsPayload;
import com.ultrabar.plugin.model.ActionSummary;
import com.ultrabar.plugin.model.CallPayload;
import com.ultrabar.plugin.callback.CallResponder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;

public class MyPluginApp {
  public static void main(String[] args) throws Exception {
    // 创建客户端（可指定 host/port）
    PluginClient client = new PluginClient("127.0.0.1", 39001);

    // 等待 TCP 连接就绪
    client.startAsync().thenRun(() -> System.out.println("TCP connected"));

    // 注册并等待 register_result
    CompletableFuture<com.fasterxml.jackson.databind.JsonNode> regFut = new CompletableFuture<>();
    RegisterPayload rp = createRegisterPayload();
    client.register(rp, new RegisterCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode payload) {
        System.out.println("register ok: " + payload.toPrettyString());
        regFut.complete(payload);
      }
      @Override
      public void onError(Throwable t) {
        regFut.completeExceptionally(t);
      }
    });

    // 在注册成功后再上报 actions
    regFut.thenAccept(p -> {
      ActionSummary a1 = new ActionSummary();
      a1.id = "music.play"; a1.version = 1; a1.name = "播放音乐"; a1.description = "在指定设备播放音乐";
      ActionsPayload ap = new ActionsPayload(Arrays.asList(a1));
      client.sendActions(ap, new ActionsCallback() {
        @Override public void onSuccess(com.fasterxml.jackson.databind.JsonNode ackPayload) {
          System.out.println("actions ack: " + ackPayload.toPrettyString());
        }
        @Override public void onError(Throwable t) { System.err.println("actions error: " + t.getMessage()); }
      });
    });

    // 处理主 App 发起的 call
    ExecutorService exec = Executors.newFixedThreadPool(4);
    client.setCallHandler((CallPayload payload, CallResponder responder) -> {
      Map<String,Object> params = payload.params;
      if (params == null || !params.containsKey("device")) {
        responder.sendError("MISSING_PARAM", "device is required", false, null);
        return;
      }
      String taskId = "task-" + java.util.UUID.randomUUID();
      responder.sendAccepted(null, taskId, "http://127.0.0.1:42101/tasks/" + taskId);
      exec.submit(() -> {
        try {
          Thread.sleep(1000);
          Map<String,Object> result = new HashMap<>();
          result.put("taskId", taskId);
          result.put("status", "ok");
          responder.sendSuccess(result);
        } catch (Exception e) {
          Map<String,Object> details = new HashMap<>();
          details.put("cause", e.getMessage());
          responder.sendError("EXEC_FAILED", "执行失败", true, details);
        }
      });
    });

    // 运行一段时间用于 demo
    Thread.sleep(20_000);
    client.stop();
    exec.shutdownNow();
  }

  private static RegisterPayload createRegisterPayload() {
    RegisterPayload rp = new RegisterPayload();
    rp.plugin = new PluginInfo();
    rp.plugin.id = "com.ultrabar.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.ultrabar.music";
    return rp;
  }
}
