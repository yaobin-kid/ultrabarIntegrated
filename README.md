# Ultrabar Integrated - Plugin SDK

这是基于 Netty 的 Ultrabar 插件协议 Java SDK 示例工程。

功能亮点：
- 使用 Netty 连接主 App（默认 127.0.0.1:39001，可通过构造函数修改）
- 提供 register、actions（上报）、describe、call 等接口
- 支持注册与 actions 的回调、actions_update 的推送回调
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

2) 运行示例（本地测试）

> 注意：示例依赖一个能够接收协议消息的服务端（默认 127.0.0.1:39001）。服务端需以换行符 `\n` 作为消息结束（Line-based framing）。

```bash
java -jar build/libs/ultrabar-plugin-sdk-1.0-SNAPSHOT.jar
```

3) 在你的项目中集成 SDK（示例代码）

下面示例展示了如何：
- 使用默认地址创建 PluginClient
- 注册插件并处理 register_result 的回调
- 上报 actions
- 查询 describe
- 调用 call

```java
import com.example.plugin.PluginClient;
import com.example.plugin.callback.RegisterCallback;
import com.example.plugin.callback.ActionsCallback;
import com.example.plugin.callback.ActionsUpdateCallback;
import com.example.plugin.model.RegisterPayload;
import com.example.plugin.model.PluginInfo;
import com.example.plugin.model.ActionsPayload;
import com.example.plugin.model.ActionSummary;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MyPluginApp {
  public static void main(String[] args) throws Exception {
    // 可选传 host/port
    PluginClient client = new PluginClient("127.0.0.1", 39001);
    client.start();

    // 注册回调
    client.register(createRegisterPayload(), new RegisterCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode registerResultPayload) {
        System.out.println("Register success: " + registerResultPayload.toPrettyString());
        // 从 registerResultPayload 中读取 sessionId / sessionToken 并保存
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Register error: " + t.getMessage());
      }
    });

    // 设置 actions_update 推送监听
    client.setActionsUpdateCallback(new ActionsUpdateCallback() {
      @Override
      public void onUpdate(com.fasterxml.jackson.databind.JsonNode updatePayload) {
        System.out.println("actions_update: " + updatePayload.toPrettyString());
      }
    });

    // 上报 actions
    ActionSummary a1 = new ActionSummary();
    a1.id = "music.play";
    a1.version = 1;
    a1.name = "播放音乐";
    a1.description = "在指定设备播放音乐";

    ActionSummary a2 = new ActionSummary();
    a2.id = "music.pause";
    a2.version = 1;
    a2.name = "暂停";
    a2.description = "暂停播放";

    ActionsPayload ap = new ActionsPayload(Arrays.asList(a1, a2));

    client.sendActions(ap, new ActionsCallback() {
      @Override
      public void onSuccess(com.fasterxml.jackson.databind.JsonNode ackPayload) {
        System.out.println("Actions ack: " + ackPayload.toPrettyString());
      }

      @Override
      public void onError(Throwable t) {
        System.err.println("Actions error: " + t.getMessage());
      }
    });

    // 示例 describe（假设已保存 sessionId 和 token）
    // client.describe(sessionId, sessionToken, "music.play").thenAccept(...)

    // 示例 call（假设已保存 sessionId 和 token）
    Map<String, Object> params = new HashMap<>();
    params.put("device", "speaker-001");
    params.put("keyword", "晴天");
    params.put("mode", "normal");

    // client.call(sessionId, sessionToken, "music.play", params).thenAccept(...)

    // 在真实程序中请优雅地停止 client.stop();
  }

  private static RegisterPayload createRegisterPayload() {
    RegisterPayload rp = new RegisterPayload();
    rp.plugin = new PluginInfo();
    rp.plugin.id = "com.example.music";
    rp.plugin.name = "Music";
    rp.plugin.version = "1.2.0";
    rp.plugin.packageName = "com.example.music";
    return rp;
  }
}
```

4) 注意事项
- Framing：当前 SDK 使用基于换行的分包（LineBasedFrameDecoder）。服务端必须每条 JSON 用 `\n` 结尾。若你需要更可靠的 framing（长度前缀），我可以在 SDK 中切换为 LengthField 编解码器。
- 身份与鉴权：register_result 会返回 sessionToken，示例未自动保存 token；生产中建议在注册成功后将 token 存储并在后续 describe/call 中传入（envelope.auth 字段）。
- 可扩展性：当前 SDK 是最小实现，包含重连和 pending 请求映射。若需自动超时、心跳、并发限制或更严格的安全，请提出，我可以继续完善。

---

如果你希望，我可以把 README 中的示例改成 Maven 方式或直接提供一个可运行的 demo 服务端用于本地联调。
