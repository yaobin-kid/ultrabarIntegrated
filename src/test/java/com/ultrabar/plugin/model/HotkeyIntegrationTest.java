package com.ultrabar.plugin.model;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 热键功能端到端集成测试：真实 Netty server + client。
 * 语义约定：每个按键至少产生 down+up 两次回调；pressType 区分 short/long；
 * 短按由 SDK 自动补齐 up（组合键 down 顺序、up 逆序）；长按 down/up 显式配对。
 */
public class HotkeyIntegrationTest {

    private static final String PKG = "com.test.hotkey";

    /** Windows VK 码：Ctrl=0x11, C=0x43, ]=0xDD(VK_OEM_6), F1=0x70 */
    private static final int VK_CONTROL = 0x11;
    private static final int VK_C = 0x43;
    private static final int VK_OEM_6 = 0xDD;
    private static final int VK_F1 = 0x70;
    private static final int VK_VOLUME_UP = 0xAF;
    private static final int VK_POWER = 0x5E;

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

    /** 启动 client 并完成注册。 */
    private void startClient() throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "HotkeyTestPlugin";
        rp.packageName = PKG;
        client.setRegisterConfig(rp);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}
        });

        client.startAsync().get(5, TimeUnit.SECONDS);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));
    }

    /** 记录所有 onHotkey 回调的 listener。 */
    private static class RecordingListener implements PluginServerListener {
        final ConcurrentLinkedQueue<HotkeyPayload> payloads = new ConcurrentLinkedQueue<HotkeyPayload>();
        boolean failAll;

        @Override
        public HotkeyResultPayload onHotkey(PluginSession session, HotkeyPayload payload) {
            payloads.add(payload);
            HotkeyResultPayload r = new HotkeyResultPayload();
            r.success = !failAll;
            if (failAll) {
                r.error = ErrorInfo.of("HOST_REJECTED", "host rejected hotkey", false, null);
            }
            return r;
        }

        HotkeyPayload poll(long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            HotkeyPayload p;
            while ((p = payloads.poll()) == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            return p;
        }

        List<HotkeyPayload> drain(int count, long timeoutMs) throws InterruptedException {
            List<HotkeyPayload> list = new ArrayList<HotkeyPayload>();
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (list.size() < count && System.currentTimeMillis() < deadline) {
                HotkeyPayload p = poll(deadline - System.currentTimeMillis());
                if (p == null) {
                    break;
                }
                list.add(p);
            }
            return list;
        }
    }

    private static void assertMsg(HotkeyPayload p, String action, String pressType, int keyCode) {
        assertNotNull(p);
        assertEquals(action, p.action);
        assertEquals(pressType, p.pressType);
        assertNotNull(p.keys);
        assertEquals(1, p.keys.size());
        assertEquals(Integer.valueOf(keyCode), p.keys.get(0));
        assertNotNull(p.timestamp);
    }

    // ====== 1. 短按组合键 Ctrl+C：服务端收到 4 次回调，down 顺序 / up 逆序 ======

    @Test
    public void testShortPressComboAutoUp() throws Exception {
        startClient();
        RecordingListener listener = new RecordingListener();
        server.setListener(listener);

        HotkeyResultPayload ack = client.sendHotkey(VK_CONTROL, VK_C).get(3, TimeUnit.SECONDS);
        assertTrue("短按上报应成功", ack.success);

        List<HotkeyPayload> msgs = listener.drain(4, 3000);
        assertEquals("组合键应产生 4 次回调（每键 down+up）", 4, msgs.size());
        assertMsg(msgs.get(0), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_SHORT, VK_CONTROL);
        assertMsg(msgs.get(1), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_SHORT, VK_C);
        assertMsg(msgs.get(2), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_SHORT, VK_C);
        assertMsg(msgs.get(3), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_SHORT, VK_CONTROL);

        // 短按不登记自动补发，之后不应再有任何回调
        Thread.sleep(500);
        assertNull("短按不应有额外回调", listener.payloads.poll());
    }

    // ====== 2. 长按 down/up 显式配对 ======

    @Test
    public void testLongPressDownUpPair() throws Exception {
        startClient();
        RecordingListener listener = new RecordingListener();
        server.setListener(listener);

        client.setHotkeyAutoReleaseMs(2000);
        HotkeyResultPayload downAck = client.hotkeyDown(VK_VOLUME_UP).get(3, TimeUnit.SECONDS);
        assertTrue(downAck.success);

        Thread.sleep(300); // 未到自动补发时间
        HotkeyResultPayload upAck = client.hotkeyUp(VK_VOLUME_UP).get(3, TimeUnit.SECONDS);
        assertTrue(upAck.success);

        List<HotkeyPayload> msgs = listener.drain(2, 3000);
        assertEquals(2, msgs.size());
        assertMsg(msgs.get(0), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_VOLUME_UP);
        assertMsg(msgs.get(1), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, VK_VOLUME_UP);

        Thread.sleep(2500); // 超过 autoReleaseMs
        assertNull("显式 up 后不应再自动补发", listener.payloads.poll());
    }

    // ====== 3. 长按长时间未 up → 自动补发 long up ======

    @Test
    public void testLongPressAutoReleaseWhenUpMissing() throws Exception {
        startClient();
        RecordingListener listener = new RecordingListener();
        server.setListener(listener);

        client.setHotkeyAutoReleaseMs(300);
        client.hotkeyDown(VK_POWER).get(3, TimeUnit.SECONDS);
        // 故意不调 hotkeyUp，等待自动补发

        List<HotkeyPayload> msgs = listener.drain(2, 3000);
        assertEquals("应收到 down + 自动补发的 up", 2, msgs.size());
        assertMsg(msgs.get(0), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_POWER);
        assertMsg(msgs.get(1), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, VK_POWER);
    }

    // ====== 4. PS Ctrl+] 持续放大画笔：重复 down 刷新补发计时 ======

    @Test
    public void testRepeatedDownRefreshesAutoRelease() throws Exception {
        startClient();
        RecordingListener listener = new RecordingListener();
        server.setListener(listener);

        client.setHotkeyAutoReleaseMs(2000); // 覆盖整个按住时长（约 600ms），不触发补发
        // 模拟按住 Ctrl+]：CTRL 按下后，以 200ms 间隔重复发送 ] 的 down
        client.hotkeyDown(VK_CONTROL).get(3, TimeUnit.SECONDS);
        for (int i = 0; i < 3; i++) {
            client.hotkeyDown(VK_OEM_6).get(3, TimeUnit.SECONDS);
            Thread.sleep(200);
        }
        client.hotkeyUp(VK_CONTROL, VK_OEM_6).get(3, TimeUnit.SECONDS); // 传参与 down 同序，内部逆序：先 ] 后 CTRL

        List<HotkeyPayload> msgs = listener.drain(6, 3000);
        assertEquals("CTRL down + 3×] down + ] up + CTRL up", 6, msgs.size());
        assertMsg(msgs.get(0), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_CONTROL);
        assertMsg(msgs.get(1), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_OEM_6);
        assertMsg(msgs.get(2), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_OEM_6);
        assertMsg(msgs.get(3), HotkeyPayload.ACTION_DOWN, HotkeyPayload.PRESS_TYPE_LONG, VK_OEM_6);
        assertMsg(msgs.get(4), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, VK_OEM_6);
        assertMsg(msgs.get(5), HotkeyPayload.ACTION_UP, HotkeyPayload.PRESS_TYPE_LONG, VK_CONTROL);

        Thread.sleep(2300); // 超过 autoReleaseMs
        assertNull("显式 up 后不应再自动补发", listener.payloads.poll());
    }

    // ====== 5. 服务端返回失败 → 回传到客户端 ======

    @Test
    public void testHotkeyFailurePropagates() throws Exception {
        startClient();
        RecordingListener listener = new RecordingListener();
        listener.failAll = true;
        server.setListener(listener);

        HotkeyResultPayload ack = client.sendHotkey(VK_F1).get(3, TimeUnit.SECONDS);

        assertFalse("宿主返回失败应回传", ack.success);
        assertNotNull(ack.error);
        assertEquals("HOST_REJECTED", ack.error.code);
    }

    // ====== 6. 实例文本消息：服务端收到 destination + instanceId + message ======

    @Test
    public void testSendMessage() throws Exception {
        startClient();
        final AtomicReference<MessagePayload> ref = new AtomicReference<MessagePayload>();
        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public MessageResultPayload onMessage(PluginSession session, MessagePayload payload) {
                ref.set(payload);
                latch.countDown();
                MessageResultPayload r = new MessageResultPayload();
                r.success = true;
                return r;
            }
        });

        MessageResultPayload ack = client
                .sendMessage("instance-001", "开始播放")
                .get(3, TimeUnit.SECONDS);

        assertTrue(ack.success);
        assertTrue("server 应收到消息", latch.await(3, TimeUnit.SECONDS));
        MessagePayload received = ref.get();
        assertEquals(MessageDestination.PC, received.destination);
        assertEquals("instance-001", received.instanceId);
        assertEquals("开始播放", received.message);
        assertNotNull(received.timestamp);
    }

    // ====== 6b. 显式指定目的地透传 ======

    @Test
    public void testSendMessageWithDestination() throws Exception {
        startClient();
        final AtomicReference<MessagePayload> ref = new AtomicReference<MessagePayload>();
        final CountDownLatch latch = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public MessageResultPayload onMessage(PluginSession session, MessagePayload payload) {
                ref.set(payload);
                latch.countDown();
                return null; // null 视为成功
            }
        });

        MessageResultPayload ack = client
                .sendMessage(MessageDestination.PC, "instance-002", "切换频道")
                .get(3, TimeUnit.SECONDS);

        assertTrue(ack.success);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(MessageDestination.PC, ref.get().destination);
        assertEquals("instance-002", ref.get().instanceId);
        assertEquals("切换频道", ref.get().message);
    }

    // ====== 7. 实例消息：宿主返回失败 → 回传客户端；空参数本地校验 ======

    @Test
    public void testMessageFailureAndValidation() throws Exception {
        startClient();
        server.setListener(new PluginServerListener() {
            @Override
            public MessageResultPayload onMessage(PluginSession session, MessagePayload payload) {
                MessageResultPayload r = new MessageResultPayload();
                r.success = false;
                r.error = ErrorInfo.of("INSTANCE_NOT_FOUND", "实例不存在", false, null);
                return r;
            }
        });

        MessageResultPayload ack = client
                .sendMessage("bad-instance", "hello")
                .get(3, TimeUnit.SECONDS);
        assertFalse(ack.success);
        assertEquals("INSTANCE_NOT_FOUND", ack.error.code);

        // 空参数在客户端本地拦截，不发起请求
        try {
            client.sendMessage("", "hello");
            fail("空 instanceId 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            client.sendMessage("instance-001", "");
            fail("空 message 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            client.sendMessage(null, "instance-001", "hello");
            fail("null destination 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ====== 8. 服务端 → 插件：sendMessage 下发，PluginListener.onMessage 接收 ======

    @Test
    public void testServerSendMessageToClient() throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                registered.countDown();
            }
        });

        final AtomicReference<MessagePayload> receivedRef = new AtomicReference<MessagePayload>();
        final CountDownLatch messageLatch = new CountDownLatch(1);
        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "HotkeyTestPlugin";
        rp.packageName = PKG;
        client.setRegisterConfig(rp);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onMessage(MessagePayload payload, com.ultrabar.plugin.callback.MessageResponder responder) {
                receivedRef.set(payload);
                messageLatch.countDown();
                responder.sendSuccess();
            }
        });
        client.startAsync().get(5, TimeUnit.SECONDS);
        assertTrue("client 注册超时", registered.await(5, TimeUnit.SECONDS));

        MessageResultPayload ack = server.sendMessage(PKG, "instance-100", "主App下发的消息")
                .get(3, TimeUnit.SECONDS);

        assertTrue("插件应回复成功", ack.success);
        assertTrue("插件应收到消息", messageLatch.await(3, TimeUnit.SECONDS));
        MessagePayload received = receivedRef.get();
        assertEquals("instance-100", received.instanceId);
        assertEquals("主App下发的消息", received.message);
        assertNull("服务端→插件方向不携带 destination", received.destination);
        assertNotNull(received.timestamp);
    }

    // ====== 9. 服务端 → 插件：插件回复失败回传服务端；未注册插件失败 ======

    @Test
    public void testServerSendMessageFailureAndNoSession() throws Exception {
        final CountDownLatch registered = new CountDownLatch(1);
        server.setListener(new PluginServerListener() {
            @Override
            public void onRegistered(PluginSession session) {
                registered.countDown();
            }
        });

        client = new PluginClient("127.0.0.1", serverPort);
        RegisterPayload rp = new RegisterPayload();
        rp.name = "HotkeyTestPlugin";
        rp.packageName = PKG;
        client.setRegisterConfig(rp);
        client.setPluginListener(new PluginListener() {
            @Override public void onRegisterSuccess(RegisterResultPayload payload) {}
            @Override public void onCall(CallPayload payload, com.ultrabar.plugin.callback.CallResponder responder) {}

            @Override
            public void onMessage(MessagePayload payload, com.ultrabar.plugin.callback.MessageResponder responder) {
                responder.sendError("NOT_SUPPORTED", "该实例不支持消息", false);
            }
        });
        client.startAsync().get(5, TimeUnit.SECONDS);
        assertTrue(registered.await(5, TimeUnit.SECONDS));

        MessageResultPayload ack = server.sendMessage(PKG, "instance-100", "hello")
                .get(3, TimeUnit.SECONDS);
        assertFalse("插件失败应回传", ack.success);
        assertEquals("NOT_SUPPORTED", ack.error.code);

        // 未注册的插件
        try {
            server.sendMessage("com.not.exists", "instance-100", "hello").get(3, TimeUnit.SECONDS);
            fail("未注册插件应失败");
        } catch (java.util.concurrent.ExecutionException expected) {
        }

        // 空参数通过 failedFuture 异步失败
        try {
            server.sendMessage(PKG, "", "hello").get(3, TimeUnit.SECONDS);
            fail("空 instanceId 应失败");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalArgumentException);
        }
    }
}
