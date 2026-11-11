package com.ultrabar.plugin.model;

import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.A2UIActionCallResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.server.PluginRegisterHandler;
import com.ultrabar.server.PluginServer;
import com.ultrabar.server.PluginServerListener;
import com.ultrabar.server.PluginSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * a2ui 功能端到端集成测试：起真实 Netty server + client，覆盖
 * client.sendA2UIData / sendA2UIUpdateDataModel → server 存储 + 回调，
 * 以及 server.callA2UIAction → client.onA2UIActionCall → responder.sendSuccess。
 *
 * 端口使用 0 让 OS 动态分配，避免并发测试端口冲突。
 */
public class A2UIIntegrationTest {

    private static final String PKG = "com.test.a2ui";

    private PluginServer server;
    private PluginClient client;
    private int serverPort;

    @Before
    public void setUp() throws Exception {
        server = new PluginServer(0);
        // 测试中默认关闭注册后自动拉取，避免与显式 sendA2UIData/queryA2UIData 产生竞态
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

    /** 创建 client，连接已启动的 server，声明 supportsA2UI，返回 session。 */
    private PluginSession startClientWithSupportsA2UI(boolean supportsA2UI) throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        final AtomicReference<PluginSession> sessionRef = new AtomicReference<PluginSession>();
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                sessionRef.set(session);
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "A2UITestPlugin";
        rp.packageName = PKG;
        rp.supportsA2UI = supportsA2UI;
        client.setRegisterConfig(rp);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
        });

        assertTrue("client 启动超时", client.startAsync().get(5, TimeUnit.SECONDS) == null);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));
        assertNotNull(sessionRef.get());
        return sessionRef.get();
    }

    // ====== 1. client.sendA2UIData → server 存储 + listener.onA2UIData ======

    @Test
    public void testSendA2UIDataStoresAndNotifies() throws Exception {
        PluginSession session = startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<A2UIDataPayload> receivedRef = new AtomicReference<A2UIDataPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIDataResultPayload onA2UIData(PluginSession s, A2UIDataPayload payload) {
                receivedRef.set(payload);
                latch.countDown();
                A2UIDataResultPayload r = new A2UIDataResultPayload();
                r.success = true;
                return r;
            }
        });

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("status", "playing");
        data.put("volume", 80);

        List<Map<String, Object>> uiList = new ArrayList<Map<String, Object>>();
        uiList.add(data);
        List<A2UIItem> items = new ArrayList<A2UIItem>();
        items.add(new A2UIItem("layout_music", "音乐", "card_music", uiList));
        A2UIDataResultPayload ack = client.sendA2UIData(items).get(3, TimeUnit.SECONDS);
        assertTrue("ack 应成功", ack.success);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(receivedRef.get());
        assertEquals(1, receivedRef.get().data.size());
        assertEquals("layout_music", receivedRef.get().data.get(0).layoutId);
        assertEquals("playing", receivedRef.get().data.get(0).ui.get(0).get("status"));
        assertEquals(80, receivedRef.get().data.get(0).ui.get(0).get("volume"));

        assertEquals(1, session.a2uiData().size());
        assertEquals("playing", session.a2uiData().get(0).ui.get(0).get("status"));
    }

    // ====== 1b. server.queryA2UIData → 触发 plugin.onA2UIDataRequest → 回传数据 ======

    @Test
    public void testQueryA2UIDataPullsFromPlugin() throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        final AtomicReference<PluginSession> sessionRef = new AtomicReference<PluginSession>();
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                sessionRef.set(session);
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "A2UITestPlugin";
        rp.packageName = PKG;
        rp.supportsA2UI = true;
        client.setRegisterConfig(rp);

        final List<A2UIItem> pluginData = new ArrayList<A2UIItem>();
        pluginData.add(new A2UIItem("layout_music", "音乐", "card_music", "{\"status\":\"playing\"}"));
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
            @Override
            public List<A2UIItem> onA2UIDataRequest() {
                return pluginData;
            }
        });

        assertTrue("client 启动超时", client.startAsync().get(5, TimeUnit.SECONDS) == null);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<A2UIDataPayload> notifyRef = new AtomicReference<A2UIDataPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIDataResultPayload onA2UIData(PluginSession s, A2UIDataPayload payload) {
                notifyRef.set(payload);
                latch.countDown();
                A2UIDataResultPayload r = new A2UIDataResultPayload();
                r.success = true;
                return r;
            }
        });

        A2UIDataRequestResultPayload result = server.queryA2UIData(PKG).get(3, TimeUnit.SECONDS);
        assertTrue("query 应成功", result.success);
        assertEquals(1, result.items.size());
        assertEquals("layout_music", result.items.get(0).layoutId);

        assertTrue("onA2UIData 应被触发", latch.await(2, TimeUnit.SECONDS));
        assertNotNull(notifyRef.get());
        assertEquals("layout_music", notifyRef.get().data.get(0).layoutId);

        assertEquals(1, sessionRef.get().a2uiData().size());
    }

    // ====== 2. client.sendA2UIUpdateDataModel → server 存储 + listener.onA2UIUpdateDataModel ======

    @Test
    public void testSendA2UIUpdateDataModelStoresAndNotifies() throws Exception {
        PluginSession session = startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<A2UIUpdateDataModelPayload> receivedRef =
                new AtomicReference<A2UIUpdateDataModelPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIUpdateDataModelResultPayload onA2UIUpdateDataModel(PluginSession s, A2UIUpdateDataModelPayload payload) {
                receivedRef.set(payload);
                latch.countDown();
                A2UIUpdateDataModelResultPayload r = new A2UIUpdateDataModelResultPayload();
                r.success = true;
                return r;
            }
        });

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("phase", "thinking");
        model.put("text", "你好");

        String modelJson = Json.mapper().writeValueAsString(model);
        A2UIUpdateDataModelResultPayload ack = client.sendA2UIUpdateDataModel("inst_music", "layout_music", modelJson, false)
                .get(3, TimeUnit.SECONDS);
        assertTrue("ack 应成功", ack.success);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(receivedRef.get());
        assertEquals("inst_music", receivedRef.get().instanceId);
        assertEquals("layout_music", receivedRef.get().layoutId);
        assertEquals(modelJson, receivedRef.get().dataModel);
        assertFalse("非增量", receivedRef.get().incremental);

        assertEquals(modelJson, session.a2uiDataModel());
    }

    @Test
    public void testSendA2UIUpdateDataModelIncremental() throws Exception {
        startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIUpdateDataModelResultPayload onA2UIUpdateDataModel(PluginSession s, A2UIUpdateDataModelPayload payload) {
                latch.countDown();
                A2UIUpdateDataModelResultPayload r = new A2UIUpdateDataModelResultPayload();
                r.success = true;
                return r;
            }
        });

        Map<String, Object> incrementalModel = new HashMap<String, Object>();
        incrementalModel.put("phase", "acting");

        String incJson = Json.mapper().writeValueAsString(incrementalModel);
        A2UIUpdateDataModelResultPayload ack = client.sendA2UIUpdateDataModel(
                "inst_music", "layout_music", incJson, true).get(3, TimeUnit.SECONDS);
        assertTrue(ack.success);
        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
    }

    // ====== 2b. listener 返回 success=false 时应原样回传给插件 ======

    @Test
    public void testSendA2UIUpdateDataModelListenerFailurePropagates() throws Exception {
        startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIUpdateDataModelResultPayload onA2UIUpdateDataModel(PluginSession s, A2UIUpdateDataModelPayload payload) {
                latch.countDown();
                A2UIUpdateDataModelResultPayload r = new A2UIUpdateDataModelResultPayload();
                r.success = false;
                r.error = ErrorInfo.of(ErrorCodes.HANDLER_EXCEPTION, "render failed", false, null);
                return r;
            }
        });

        A2UIUpdateDataModelResultPayload ack = client.sendA2UIUpdateDataModel(
                "inst_music", "layout_music", "{\"phase\":\"thinking\"}", false).get(3, TimeUnit.SECONDS);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertFalse("宿主失败时 ack.success 应为 false", ack.success);
        assertNotNull(ack.error);
        assertEquals(ErrorCodes.HANDLER_EXCEPTION, ack.error.code);
        assertEquals("render failed", ack.error.message);
    }

    // ====== 2c. listener 抛异常时服务端应返回 HANDLER_EXCEPTION ======

    @Test
    public void testSendA2UIDataListenerExceptionPropagates() throws Exception {
        startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIDataResultPayload onA2UIData(PluginSession s, A2UIDataPayload payload) {
                latch.countDown();
                throw new RuntimeException("boom");
            }
        });

        List<A2UIItem> items = new ArrayList<A2UIItem>();
        items.add(new A2UIItem("layout_music", "音乐", "card_music",
                "{\"status\":\"playing\"}")); // 字符串内部解析为数组
        A2UIDataResultPayload ack = client.sendA2UIData(items).get(3, TimeUnit.SECONDS);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertFalse("listener 抛异常时 ack.success 应为 false", ack.success);
        assertNotNull(ack.error);
        assertEquals(ErrorCodes.HANDLER_EXCEPTION, ack.error.code);
    }

    // ====== 3. server.callA2UIAction → client.onA2UIActionCall → responder.sendSuccess ======

    @Test
    public void testCallA2UIActionOk() throws Exception {
        startClientWithSupportsA2UI(true);

        final CountDownLatch handled = new CountDownLatch(1);
        final AtomicReference<String> actionNameRef = new AtomicReference<String>();
        final AtomicReference<Map<String, Object>> contextRef = new AtomicReference<Map<String, Object>>();

        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
                actionNameRef.set(payload.actionName);
                contextRef.set(payload.context);
                Map<String, Object> result = new HashMap<String, Object>();
                result.put("reply", "hello back");
                responder.sendSuccess(result);
                handled.countDown();
            }
        });

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("prompt", "hello");

        A2UIActionCallResultPayload result = server.callA2UIAction(PKG, "a2ui_chat", "layout_music", "card_music", params)
                .get(3, TimeUnit.SECONDS);

        assertTrue("call 应成功", result.success);
        assertEquals("hello back", ((Map<?, ?>) result.result).get("reply"));

        assertTrue("client 应收到 action call", handled.await(3, TimeUnit.SECONDS));
        assertEquals("a2ui_chat", actionNameRef.get());
        assertEquals(params, contextRef.get());
    }

    @Test
    public void testCallA2UIActionClientRespondsError() throws Exception {
        startClientWithSupportsA2UI(true);

        final CountDownLatch handled = new CountDownLatch(1);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onA2UIActionCall(A2UIActionCallPayload payload, A2UIActionCallResponder responder) {
                responder.sendError("NO_SUPPORT", "unknown action", false);
                handled.countDown();
            }
        });

        A2UIActionCallResultPayload result = server.callA2UIAction(PKG, "a2ui_reset", "layout_music", null, null)
                .get(3, TimeUnit.SECONDS);

        assertFalse("客户端拒绝时 result.success 应为 false", result.success);
        assertNotNull(result.error);
        assertEquals("NO_SUPPORT", result.error.code);
        assertTrue(handled.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void testCallA2UIActionRejectedWhenNotSupports() throws Exception {
        startClientWithSupportsA2UI(false);

        CompletableFuture<A2UIActionCallResultPayload> future =
                server.callA2UIAction(PKG, "a2ui_chat", "layout_music", null, null);

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("supportsA2UI=false 时应抛 Exception");
        } catch (java.util.concurrent.ExecutionException ex) {
            assertTrue("错误信息应包含 supports a2ui",
                    ex.getCause().getMessage().contains("does not support a2ui"));
        }
    }

    @Test
    public void testCallA2UIActionUnknownPackage() throws Exception {
        startClientWithSupportsA2UI(true);

        CompletableFuture<A2UIActionCallResultPayload> future =
                server.callA2UIAction("com.unknown.pkg", "a2ui_chat", "layout_music", null, null);

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("未知包名应抛 Exception");
        } catch (java.util.concurrent.ExecutionException ex) {
            assertTrue("错误信息应包含 no active session",
                    ex.getCause().getMessage().contains("no active session"));
        }
    }

    @Test
    public void testCallA2UIActionTimeoutWhenClientSilent() throws Exception {
        startClientWithSupportsA2UI(true);

        // 不实现 onA2UIActionCall，让插件默认返回 NO_HANDLER
        // 但我们不期望默认处理——测试的是默认 NO_HANDLER 立即响应后 server 端 result.error 被设置
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
            // onA2UIActionCall 不实现，走 PluginListener 默认：responder.sendError(NO_HANDLER, ...)
        });

        A2UIActionCallResultPayload result = server.callA2UIAction(PKG, "a2ui_chat", "layout_music", null, null)
                .get(3, TimeUnit.SECONDS);

        assertFalse("默认 NO_HANDLER 应返回 false", result.success);
        assertNotNull(result.error);
        assertEquals("NO_HANDLER", result.error.code);
    }

    // ====== 1c. sendA2UIData 批量推送多个 layout ======

    @Test
    public void testSendA2UIDataMultipleItems() throws Exception {
        PluginSession session = startClientWithSupportsA2UI(true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<A2UIDataPayload> receivedRef = new AtomicReference<A2UIDataPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public A2UIDataResultPayload onA2UIData(PluginSession s, A2UIDataPayload payload) {
                receivedRef.set(payload);
                latch.countDown();
                A2UIDataResultPayload r = new A2UIDataResultPayload();
                r.success = true;
                return r;
            }
        });

        List<A2UIItem> items = new ArrayList<A2UIItem>();
        items.add(new A2UIItem("layout_a", "a", "card_a", "{\"status\":\"playing\"}"));
        items.add(new A2UIItem("layout_b", "b", "card_b", "{\"status\":\"paused\"}"));
        A2UIDataResultPayload ack = client.sendA2UIData(items).get(3, TimeUnit.SECONDS);
        assertTrue("ack 应成功", ack.success);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(receivedRef.get());
        assertEquals(2, receivedRef.get().data.size());
        assertEquals("layout_a", receivedRef.get().data.get(0).layoutId);
        assertEquals("layout_b", receivedRef.get().data.get(1).layoutId);

        assertEquals(2, session.a2uiData().size());
    }

    // ====== 1d. queryA2UIData 插件返回空列表 ======

    @Test
    public void testQueryA2UIDataEmptyList() throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "A2UITestPlugin";
        rp.packageName = PKG;
        rp.supportsA2UI = true;
        client.setRegisterConfig(rp);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
            // onA2UIDataRequest 不实现 → 默认返回空列表
        });

        assertTrue("client 启动超时", client.startAsync().get(5, TimeUnit.SECONDS) == null);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));

        A2UIDataRequestResultPayload result = server.queryA2UIData(PKG).get(3, TimeUnit.SECONDS);
        assertTrue("query 应成功", result.success);
        assertNotNull(result.items);
        assertEquals(0, result.items.size());
    }

    // ====== 1e. queryA2UIData 对未声明 supportsA2UI 的插件拒绝 ======

    @Test
    public void testQueryA2UIDataRejectedWhenNotSupports() throws Exception {
        startClientWithSupportsA2UI(false);

        try {
            server.queryA2UIData(PKG).get(3, TimeUnit.SECONDS);
            fail("应抛出异常");
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("应为 IllegalStateException", cause instanceof IllegalStateException);
            assertTrue("错误信息应包含 does not support",
                    cause.getMessage().contains("does not support a2ui"));
        }
    }

    // ====== 1f. 注册成功后自动拉取 a2ui 数据到 PluginSession ======

    @Test
    public void testAutoPullA2UIDataOnRegisterPopulatesSession() throws Exception {
        server.setAutoPullA2UIDataOnRegister(true);

        final CountDownLatch registered = new CountDownLatch(1);
        final AtomicReference<PluginSession> sessionRef = new AtomicReference<PluginSession>();
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                sessionRef.set(session);
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "A2UITestPlugin";
        rp.packageName = PKG;
        rp.supportsA2UI = true;
        client.setRegisterConfig(rp);

        final List<A2UIItem> pluginData = new ArrayList<A2UIItem>();
        pluginData.add(new A2UIItem("layout_music", "音乐", "card_music", "{\"status\":\"playing\"}"));
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
            @Override
            public List<A2UIItem> onA2UIDataRequest() {
                return pluginData;
            }
        });

        assertTrue("client 启动超时", client.startAsync().get(5, TimeUnit.SECONDS) == null);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));

        PluginSession session = sessionRef.get();
        assertNotNull(session);

        // a2uiData 初始为 emptyList，等待自动拉取填充实际数据
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && session.a2uiData().isEmpty()) {
            Thread.sleep(20);
        }
        assertFalse("注册后应自动拉取 a2ui 数据", session.a2uiData().isEmpty());
        assertEquals(1, session.a2uiData().size());
        assertEquals("layout_music", session.a2uiData().get(0).layoutId);
    }
}

