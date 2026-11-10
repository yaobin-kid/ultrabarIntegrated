package com.ultrabar.plugin.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.server.PluginRegisterHandler;
import com.ultrabar.server.PluginServer;
import com.ultrabar.server.PluginServerListener;
import com.ultrabar.server.PluginSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 卡片参数 schema（args）测试：6 种类型的 Builder、A2UIItem.args 序列化契约、
 * 以及动态 schema 的端到端拉取链路（PluginServer.queryA2UIArgsSchema → 插件回调）。
 */
public class A2UIArgsTest {

    private static final String PKG = "com.test.args";

    private PluginServer server;
    private PluginClient client;
    private int serverPort;

    @Before
    public void setUp() throws Exception {
        server = new PluginServer(0);
        server.setAutoPullA2UIDataOnRegister(false);
        server.setRegisterHandler(new PluginRegisterHandler() {
            @Override
            public RegisterResultPayload handleRegister(RegisterPayload request) {
                RegisterResultPayload result = new RegisterResultPayload();
                result.success = true;
                result.configServer = new ConfigServer();
                result.configServer.port = 100;
                result.sessionToken = UUID.randomUUID().toString();
                return result;
            }
        });
        server.start();
        serverPort = server.getPort();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null) {
            try {
                client.stop();
            } catch (Exception ignored) {
            }
            client = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    private void startClient(final PluginListener pluginListener) throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "ArgsTestPlugin";
        rp.packageName = PKG;
        rp.supportsA2UI = true;
        client.setRegisterConfig(rp);
        client.setPluginListener(pluginListener);

        client.startAsync().get(5, TimeUnit.SECONDS);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));
    }

    private static PluginListener base() {
        return new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload payload) {
            }

            @Override
            public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {
            }
        };
    }

    // ====== 1. 6 种类型 Builder 生成正确的线上结构 ======

    @Test
    public void testSixArgTypesWireFormat() throws Exception {
        A2UIArgs args = A2UIArgs.create()
                .arg("title", A2UIArgSpec.stringArg().label("标题").defaultValue("参数演示").build())
                .arg("count", A2UIArgSpec.intArg().label("数量").defaultValue(3).min(1).max(99).build())
                .arg("ratio", A2UIArgSpec.floatArg().label("比例").defaultValue(0.5).min(0.0).max(1.0).build())
                .arg("enabled", A2UIArgSpec.boolArg().label("启用").defaultValue(true).build())
                .arg("level", A2UIArgSpec.selectArg().label("等级").defaultValue("normal")
                        .option("low", "低").option("normal", "中").option("high", "高").build())
                .arg("headers", A2UIArgSpec.objectArg().label("嵌套参数组")
                        .property("Content-Type", A2UIArgSpec.stringArg()
                                .defaultValue("application/json").build())
                        .property("retries", A2UIArgSpec.intArg().label("重试次数")
                                .defaultValue(3).min(0).max(10).build())
                        .build());




        // 6 个顶层静态参数（覆盖 6 种类型）
        assertEquals(6, args.argNames().size());
        assertFalse(args.isDynamic());

        JsonNode root = Json.mapper().readTree(Json.mapper().writeValueAsString(args));
        System.out.println(root.toString());

        assertEquals("string", root.get("title").get("type").asText());
        assertEquals("参数演示", root.get("title").get("default").asText());
        assertEquals("标题", root.get("title").get("label").asText());

        assertEquals("int", root.get("count").get("type").asText());
        assertEquals(3, root.get("count").get("default").asInt());
        assertEquals(1, root.get("count").get("min").asInt());
        assertEquals(99, root.get("count").get("max").asInt());
        // int 参数不应带 options / properties
        assertNull(root.get("count").get("options"));
        assertNull(root.get("count").get("properties"));

        assertEquals("float", root.get("ratio").get("type").asText());
        assertEquals(0.5, root.get("ratio").get("default").asDouble(), 0.0001);
        assertEquals(1.0, root.get("ratio").get("max").asDouble(), 0.0001);

        assertEquals("bool", root.get("enabled").get("type").asText());
        assertTrue(root.get("enabled").get("default").asBoolean());

        assertEquals("select", root.get("level").get("type").asText());
        assertEquals("normal", root.get("level").get("default").asText());
        JsonNode options = root.get("level").get("options");
        assertEquals(3, options.size());
        assertEquals("low", options.get(0).get("value").asText());
        assertEquals("低", options.get(0).get("label").asText());

        assertEquals("object", root.get("headers").get("type").asText());
        JsonNode props = root.get("headers").get("properties");
        assertEquals("application/json", props.get("Content-Type").get("default").asText());
        assertEquals(10, props.get("retries").get("max").asInt());
        // object 参数不应带 default / min / max
        assertNull(root.get("headers").get("default"));
        assertNull(root.get("headers").get("min"));
    }

    // ====== 2. select 简写（值即标签） ======

    @Test
    public void testSelectSimpleOption() throws Exception {
        A2UIArgSpec select = A2UIArgSpec.selectArg().defaultValue("GET")
                .option("GET").option("POST").build();
        JsonNode node = Json.mapper().readTree(Json.mapper().writeValueAsString(select));
        assertEquals(2, node.get("options").size());
        assertTrue("简写 option 序列化为纯字符串", node.get("options").get(0).isTextual());
        assertEquals("GET", node.get("options").get(0).asText());
    }

    // ====== 3. Builder 校验：非法参数在 build 时被拦截 ======

    @Test
    public void testBuilderValidation() {
        try {
            A2UIArgSpec.intArg().min(10).max(1).build();
            fail("min > max 应被拦截");
        } catch (IllegalArgumentException expected) {
        }
        try {
            A2UIArgSpec.selectArg().build();
            fail("select 无 option 应被拦截");
        } catch (IllegalArgumentException expected) {
        }
        try {
            A2UIArgSpec.objectArg().build();
            fail("object 无 property 应被拦截");
        } catch (IllegalArgumentException expected) {
        }
        try {
            A2UIArgs.create().arg("dynamic", A2UIArgSpec.stringArg().build());
            fail("保留字段名应被拦截");
        } catch (IllegalArgumentException expected) {
        }
        try {
            A2UIArgs.dynamic("");
            fail("空 schemaGetter 应被拦截");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ====== 4. A2UIItem.args 序列化 + 反序列化往返 ======

    @Test
    public void testA2UIItemWithArgsRoundTrip() throws Exception {
        A2UIArgs args = A2UIArgs.create()
                .arg("title", A2UIArgSpec.stringArg().defaultValue("x").build());
        A2UIItem item = new A2UIItem("layout-1", "card-1", "name-1", "[]", args);

        String json = Json.mapper().writeValueAsString(item);
        JsonNode root = Json.mapper().readTree(json);
        assertEquals("string", root.get("args").get("title").get("type").asText());
        assertEquals("x", root.get("args").get("title").get("default").asText());

        A2UIItem back = Json.mapper().readValue(json, A2UIItem.class);
        assertEquals(1, back.args.argNames().size());
        assertEquals("x", back.args.specs().get("title").defaultValue);

        // 无 args 时不应出现在线上 JSON 中
        A2UIItem noArgs = new A2UIItem("layout-2", "card-2", "name-2", "[]");
        assertNull(noArgs.args);
        assertFalse(Json.mapper().writeValueAsString(noArgs).contains("\"args\""));
    }

    // ====== 5. 动态 schema 端到端：服务端拉取 → 插件回调 → 返回响应数据 ======

    @Test
    public void testDynamicArgsSchemaEndToEnd() throws Exception {
        final AtomicReference<String> receivedGetter = new AtomicReference<String>();
        startClient(new PluginListener() {
            @Override
            public void onRegisterSuccess(RegisterResultPayload payload) {
            }

            @Override
            public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {
            }

            @Override
            public Map<String, A2UIArgSpec> onA2UIArgsSchemaRequest(String schemaGetter) {
                receivedGetter.set(schemaGetter);
                Map<String, A2UIArgSpec> schema = new LinkedHashMap<String, A2UIArgSpec>();
                schema.put("fieldA", A2UIArgSpec.stringArg().label("字段A").defaultValue("v1").build());
                schema.put("fieldB", A2UIArgSpec.intArg().label("字段B").min(1).max(100).build());
                return schema;
            }
        });

        A2UIArgsSchemaResultPayload result = server
                .queryA2UIArgsSchema(PKG, "schema.argsdemo")
                .get(5, TimeUnit.SECONDS);

        assertTrue("拉取动态 schema 应成功", result.success);
        assertEquals("schema.argsdemo", receivedGetter.get());
        assertNotNull(result.schema);
        assertEquals(2, result.schema.size());
        assertEquals(A2UIArgSpec.TYPE_STRING, result.schema.get("fieldA").type);
        assertEquals("字段A", result.schema.get("fieldA").label);
        assertEquals("v1", result.schema.get("fieldA").defaultValue);
        assertEquals(A2UIArgSpec.TYPE_INT, result.schema.get("fieldB").type);
        assertEquals(100, result.schema.get("fieldB").max.intValue());
    }

    // ====== 6. 插件未处理动态 schema → 空 Map；非法参数本地拦截 ======

    @Test
    public void testDynamicArgsSchemaDefaultEmptyAndValidation() throws Exception {
        startClient(base()); // 未 override onA2UIArgsSchemaRequest

        A2UIArgsSchemaResultPayload result = server
                .queryA2UIArgsSchema(PKG, "schema.none")
                .get(5, TimeUnit.SECONDS);
        assertTrue(result.success);
        assertNotNull("未处理时应归一化为空 Map", result.schema);
        assertTrue(result.schema.isEmpty());

        try {
            server.queryA2UIArgsSchema(PKG, "").get(3, TimeUnit.SECONDS);
            fail("空 schemaGetter 应失败");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
        }
    }

    // ====== 7. 动态 schema 声明的线上格式：dynamic + schemaGetter 扁平混入 ======

    @Test
    public void testDynamicArgsWireFormat() throws Exception {
        A2UIArgs args = A2UIArgs.dynamic("schema.argsdemo")
                .arg("title", A2UIArgSpec.stringArg().defaultValue("标题").build());

        assertTrue(args.isDynamic());
        assertEquals("schema.argsdemo", args.schemaGetter());
        assertEquals(1, args.argNames().size());

        JsonNode root = Json.mapper().readTree(Json.mapper().writeValueAsString(args));
        assertTrue(root.get("dynamic").asBoolean());
        assertEquals("schema.argsdemo", root.get("schemaGetter").asText());
        assertEquals("string", root.get("title").get("type").asText());

        // 反序列化后仍能区分保留字段与参数
        A2UIArgs back = Json.mapper().readValue(root.toString(), A2UIArgs.class);
        assertTrue(back.isDynamic());
        assertEquals("schema.argsdemo", back.schemaGetter());
        assertEquals(1, back.argNames().size());
    }

    // ====== 8. 从原始 JSON 字符串反向解析为 A2UIArgs（含嵌套 object / select） ======

    @Test
    public void testParseComplexArgsFromRawJson() throws Exception {
        String json = "{"
                + "\"title\":{\"type\":\"string\",\"label\":\"标题\",\"default\":\"参数演示\"},"
                + "\"count\":{\"type\":\"int\",\"label\":\"数量\",\"min\":1,\"max\":99,\"default\":3},"
                + "\"ratio\":{\"type\":\"float\",\"label\":\"比例\",\"min\":0,\"max\":1,\"default\":0.5},"
                + "\"enabled\":{\"type\":\"bool\",\"label\":\"启用\",\"default\":true},"
                + "\"level\":{\"type\":\"select\",\"label\":\"等级\","
                + "  \"options\":[{\"value\":\"low\",\"label\":\"低\"},{\"value\":\"normal\",\"label\":\"中\"},{\"value\":\"high\",\"label\":\"高\"}],"
                + "  \"default\":\"normal\"},"
                + "\"headers\":{\"type\":\"object\",\"label\":\"嵌套参数组\",\"properties\":{"
                + "  \"Content-Type\":{\"type\":\"string\",\"default\":\"application/json\"},"
                + "  \"retries\":{\"type\":\"int\",\"label\":\"重试次数\",\"min\":0,\"max\":10,\"default\":3}}}"
                + "}";

        A2UIArgs args = Json.mapper().readValue(json, A2UIArgs.class);

        assertEquals("6 个顶层参数全部解析出来", 6, args.argNames().size());
        Map<String, A2UIArgSpec> specs = args.specs();

        assertEquals(A2UIArgSpec.TYPE_STRING, specs.get("title").type);
        assertEquals("参数演示", specs.get("title").defaultValue);

        assertEquals(A2UIArgSpec.TYPE_INT, specs.get("count").type);
        assertEquals(99, specs.get("count").max.intValue());
        assertEquals(1, specs.get("count").min.intValue());
        assertEquals(3, specs.get("count").defaultValue);

        assertEquals(A2UIArgSpec.TYPE_FLOAT, specs.get("ratio").type);
        assertEquals(0.5, ((Number) specs.get("ratio").defaultValue).doubleValue(), 0.0001);

        assertEquals(A2UIArgSpec.TYPE_BOOL, specs.get("enabled").type);
        assertEquals(Boolean.TRUE, specs.get("enabled").defaultValue);

        A2UIArgSpec level = specs.get("level");
        assertEquals(A2UIArgSpec.TYPE_SELECT, level.type);
        assertEquals("normal", level.defaultValue);
        assertEquals(3, level.options.size());

        // 嵌套 object 的 properties 也自动递归成 A2UIArgSpec
        A2UIArgSpec headers = specs.get("headers");
        assertEquals(A2UIArgSpec.TYPE_OBJECT, headers.type);
        assertEquals(2, headers.properties.size());
        assertEquals("application/json", headers.properties.get("Content-Type").defaultValue);
        assertEquals(10, headers.properties.get("retries").max.intValue());
        assertEquals(0, headers.properties.get("retries").min.intValue());
    }
}
