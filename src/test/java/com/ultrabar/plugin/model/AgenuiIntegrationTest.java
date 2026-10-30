package com.ultrabar.plugin.model;

import com.ultrabar.plugin.PluginClient;
import com.ultrabar.plugin.callback.AgenuiActionCallResponder;
import com.ultrabar.plugin.callback.PluginListener;
import com.ultrabar.server.PluginRegisterHandler;
import com.ultrabar.server.PluginServer;
import com.ultrabar.server.PluginServerListener;
import com.ultrabar.server.PluginSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * agenui 功能端到端集成测试：起真实 Netty server + client，覆盖
 * client.sendAgenuiData / sendAgenuiUpdateDataModel → server 存储 + 回调，
 * 以及 server.callAgenuiAction → client.onAgenuiActionCall → responder.sendSuccess。
 *
 * 端口使用 0 让 OS 动态分配，避免并发测试端口冲突。
 */
public class AgenuiIntegrationTest {

    private static final String PKG = "com.test.agenui";

    private PluginServer server;
    private PluginClient client;
    private int serverPort;

    @Before
    public void setUp() throws Exception {
        server = new PluginServer(0);
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

    /** 创建 client，连接已启动的 server，声明 supportsAgenui，返回 session。 */
    private PluginSession startClientWithSupportsAgenui(boolean supportsAgenui) throws Exception {
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
        rp.name = "AgenuiTestPlugin";
        rp.packageName = PKG;
        rp.supportsAgenui = supportsAgenui;
        client.setRegisterConfig(rp);

        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));
        assertNotNull(sessionRef.get());
        return sessionRef.get();
    }

    // ====== 1. client.sendAgenuiData → server 存储 + listener.onAgenuiData ======

    @Test
    public void testSendAgenuiDataStoresAndNotifies() throws Exception {
        PluginSession session = startClientWithSupportsAgenui(true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<AgenuiDataPayload> receivedRef = new AtomicReference<AgenuiDataPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public void onAgenuiData(PluginSession s, AgenuiDataPayload payload) {
                receivedRef.set(payload);
                latch.countDown();
            }
        });

        Map<String, Object> data = new HashMap<String, Object>();
        data.put("status", "playing");
        data.put("volume", 80);

        AgenuiDataResultPayload ack = client.sendAgenuiData("audio_event", data).get(3, TimeUnit.SECONDS);
        assertTrue("ack 应成功", ack.success);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(receivedRef.get());
        assertEquals("audio_event", receivedRef.get().dataType);
        assertEquals(data, receivedRef.get().data);

        assertEquals(data, session.agenuiData("audio_event"));
    }

    // ====== 2. client.sendAgenuiUpdateDataModel → server 存储 + listener.onAgenuiUpdateDataModel ======

    @Test
    public void testSendAgenuiUpdateDataModelStoresAndNotifies() throws Exception {
        PluginSession session = startClientWithSupportsAgenui(true);

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<AgenuiUpdateDataModelPayload> receivedRef =
                new AtomicReference<AgenuiUpdateDataModelPayload>();
        server.setListener(new PluginServerListener() {
            @Override
            public void onAgenuiUpdateDataModel(PluginSession s, AgenuiUpdateDataModelPayload payload) {
                receivedRef.set(payload);
                latch.countDown();
            }
        });

        Map<String, Object> model = new HashMap<String, Object>();
        model.put("phase", "thinking");
        model.put("text", "你好");

        AgenuiUpdateDataModelResultPayload ack = client.sendAgenuiUpdateDataModel("agent_state", model, false)
                .get(3, TimeUnit.SECONDS);
        assertTrue("ack 应成功", ack.success);

        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(receivedRef.get());
        assertEquals("agent_state", receivedRef.get().modelType);
        assertEquals(model, receivedRef.get().dataModel);
        assertFalse("非增量", receivedRef.get().incremental);

        assertEquals(model, session.agenuiDataModel("agent_state"));
    }

    @Test
    public void testSendAgenuiUpdateDataModelIncremental() throws Exception {
        startClientWithSupportsAgenui(true);

        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onAgenuiUpdateDataModel(PluginSession s, AgenuiUpdateDataModelPayload payload) {
                latch.countDown();
            }
        });

        Map<String, Object> incrementalModel = new HashMap<String, Object>();
        incrementalModel.put("phase", "acting");

        AgenuiUpdateDataModelResultPayload ack = client.sendAgenuiUpdateDataModel(
                "agent_state", incrementalModel, true).get(3, TimeUnit.SECONDS);
        assertTrue(ack.success);
        assertTrue("listener 应收到回调", latch.await(3, TimeUnit.SECONDS));
    }

    // ====== 3. server.callAgenuiAction → client.onAgenuiActionCall → responder.sendSuccess ======

    @Test
    public void testCallAgenuiActionOk() throws Exception {
        startClientWithSupportsAgenui(true);

        final CountDownLatch handled = new CountDownLatch(1);
        final AtomicReference<String> actionIdRef = new AtomicReference<String>();
        final AtomicReference<Map<?, ?>> paramsRef = new AtomicReference<Map<?, ?>>();

        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onAgenuiActionCall(AgenuiActionCallPayload payload, AgenuiActionCallResponder responder) {
                actionIdRef.set(payload.actionId);
                paramsRef.set(payload.params);
                Map<String, Object> result = new HashMap<String, Object>();
                result.put("reply", "hello back");
                responder.sendSuccess(result);
                handled.countDown();
            }
        });

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("prompt", "hello");

        AgenuiActionCallResultPayload result = server.callAgenuiAction(PKG, "agenui_chat", params)
                .get(3, TimeUnit.SECONDS);

        assertTrue("call 应成功", result.success);
        assertEquals("hello back", ((Map<?, ?>) result.result).get("reply"));

        assertTrue("client 应收到 action call", handled.await(3, TimeUnit.SECONDS));
        assertEquals("agenui_chat", actionIdRef.get());
        assertEquals(params, paramsRef.get());
    }

    @Test
    public void testCallAgenuiActionClientRespondsError() throws Exception {
        startClientWithSupportsAgenui(true);

        final CountDownLatch handled = new CountDownLatch(1);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onAgenuiActionCall(AgenuiActionCallPayload payload, AgenuiActionCallResponder responder) {
                responder.sendError("NO_SUPPORT", "unknown action", false);
                handled.countDown();
            }
        });

        AgenuiActionCallResultPayload result = server.callAgenuiAction(PKG, "agenui_reset", null)
                .get(3, TimeUnit.SECONDS);

        assertFalse("客户端拒绝时 result.success 应为 false", result.success);
        assertNotNull(result.error);
        assertEquals("NO_SUPPORT", result.error.code);
        assertTrue(handled.await(3, TimeUnit.SECONDS));
    }

    @Test
    public void testCallAgenuiActionRejectedWhenNotSupports() throws Exception {
        startClientWithSupportsAgenui(false);

        CompletableFuture<AgenuiActionCallResultPayload> future =
                server.callAgenuiAction(PKG, "agenui_chat", null);

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("supportsAgenui=false 时应抛 Exception");
        } catch (java.util.concurrent.ExecutionException ex) {
            assertTrue("错误信息应包含 supports agenui",
                    ex.getCause().getMessage().contains("does not support agenui"));
        }
    }

    @Test
    public void testCallAgenuiActionUnknownPackage() throws Exception {
        startClientWithSupportsAgenui(true);

        CompletableFuture<AgenuiActionCallResultPayload> future =
                server.callAgenuiAction("com.unknown.pkg", "agenui_chat", null);

        try {
            future.get(1, TimeUnit.SECONDS);
            fail("未知包名应抛 Exception");
        } catch (java.util.concurrent.ExecutionException ex) {
            assertTrue("错误信息应包含 no active session",
                    ex.getCause().getMessage().contains("no active session"));
        }
    }

    @Test
    public void testCallAgenuiActionTimeoutWhenClientSilent() throws Exception {
        startClientWithSupportsAgenui(true);

        // 不实现 onAgenuiActionCall，让插件默认返回 NO_HANDLER
        // 但我们不期望默认处理——测试的是默认 NO_HANDLER 立即响应后 server 端 result.error 被设置
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
            // onAgenuiActionCall 不实现，走 PluginListener 默认：responder.sendError(NO_HANDLER, ...)
        });

        AgenuiActionCallResultPayload result = server.callAgenuiAction(PKG, "agenui_chat", null)
                .get(3, TimeUnit.SECONDS);

        assertFalse("默认 NO_HANDLER 应返回 false", result.success);
        assertNotNull(result.error);
        assertEquals("NO_HANDLER", result.error.code);
    }
}

