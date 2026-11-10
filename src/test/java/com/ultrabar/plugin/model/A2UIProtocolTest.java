package com.ultrabar.plugin.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * a2ui 协议载荷的构造与序列化单元测试。
 */
public class A2UIProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    public void testA2UIDataPayloadFields() {
        A2UIItem item = new A2UIItem("layout_music", "音乐", "card_music",
                "[{\"status\":\"playing\",\"volume\":80}]");
        java.util.List<A2UIItem> items = new java.util.ArrayList<A2UIItem>();
        items.add(item);
        A2UIDataPayload p = new A2UIDataPayload(items);
        assertEquals(1, p.data.size());
        assertEquals("layout_music", p.data.get(0).layoutId);
        assertNotNull(p.data.get(0).ui);
        assertEquals(1, p.data.get(0).ui.size());
        assertEquals("playing", ((Map<String, Object>) p.data.get(0).ui.get(0)).get("status"));
        assertEquals(80, ((Map<String, Object>) p.data.get(0).ui.get(0)).get("volume"));
        assertNotNull(p.timestamp);
        assertTrue(p.timestamp > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testA2UIDataPayloadJson() throws Exception {
        A2UIItem item = new A2UIItem("layout_music", "音乐", "card_music",
                "[{\"status\":\"playing\"}]");
        java.util.List<A2UIItem> items = new java.util.ArrayList<A2UIItem>();
        items.add(item);
        A2UIDataPayload p = new A2UIDataPayload(items);
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("playing"));
        assertTrue(json.contains("layout_music"));

        A2UIDataPayload round = mapper.readValue(json, A2UIDataPayload.class);
        assertEquals(1, round.data.size());
        assertEquals("layout_music", round.data.get(0).layoutId);
        assertEquals(1, round.data.get(0).ui.size());
        assertEquals("playing", ((Map<String, Object>) round.data.get(0).ui.get(0)).get("status"));
    }

    @Test
    public void testA2UIItemStringUiParsedAsArray() {
        // 字符串参数内部解析为 JSON 数组
        A2UIItem item = new A2UIItem("layout_music", "音乐", "card_music",
                "{\"status\":\"playing\"}");
        assertEquals("layout_music", item.layoutId);
        assertNotNull(item.ui);
        assertEquals(1, item.ui.size());
        assertEquals("playing", item.ui.get(0).get("status"));

        // 数组字符串
        A2UIItem arr = new A2UIItem("layout_x", "x", "card_x", "[{\"a\":1},{\"b\":2}]");
        assertEquals(2, arr.ui.size());

        // 非 JSON 字符串抛 IllegalArgumentException
        try {
            new A2UIItem("bad", "name", "card", "not-json");
            fail("非法 JSON 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testA2UIUpdateDataModelPayloadDefaultIncremental() {
        A2UIUpdateDataModelPayload p = new A2UIUpdateDataModelPayload("layout_music", "{\"phase\":\"thinking\"}");
        assertEquals("layout_music", p.layoutId);
        assertEquals("{\"phase\":\"thinking\"}", p.dataModel);
        assertFalse(p.incremental);
        assertNotNull(p.timestamp);
    }

    @Test
    public void testA2UIUpdateDataModelPayloadIncrementalTrue() {
        A2UIUpdateDataModelPayload p = new A2UIUpdateDataModelPayload();
        p.dataModel = "{\"phase\":\"acting\"}";
        p.incremental = true;
        p.timestamp = 123L;

        assertEquals("{\"phase\":\"acting\"}", p.dataModel);
        assertTrue(p.incremental);
        assertEquals(Long.valueOf(123L), p.timestamp);
    }

    @Test
    public void testA2UIUpdateDataModelPayloadJson() throws Exception {
        A2UIUpdateDataModelPayload p = new A2UIUpdateDataModelPayload("layout_music", "{\"phase\":\"thinking\"}");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("thinking"));
        assertTrue(json.contains("layout_music"));

        A2UIUpdateDataModelPayload round = mapper.readValue(json, A2UIUpdateDataModelPayload.class);
        assertEquals("layout_music", round.layoutId);
        assertEquals("{\"phase\":\"thinking\"}", round.dataModel);
    }

    @Test
    public void testA2UIActionCallPayloadFields() {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("prompt", "hello");

        A2UIActionCallPayload p = new A2UIActionCallPayload("a2ui_chat", "layout_music", "card_music", args);
        assertEquals("a2ui_chat", p.actionName);
        assertEquals("layout_music", p.layoutId);
        assertEquals("card_music", p.cardId);
        assertEquals(args, p.context);
    }

    @Test
    public void testA2UIActionCallPayloadJson() throws Exception {
        A2UIActionCallPayload p = new A2UIActionCallPayload("a2ui_reset", "layout_music", null, null);
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("a2ui_reset"));

        A2UIActionCallPayload round = mapper.readValue(json, A2UIActionCallPayload.class);
        assertEquals("a2ui_reset", round.actionName);
        assertNull(round.context);
        assertEquals("layout_music", round.layoutId);
        assertNull(round.cardId);
    }

    @Test
    public void testA2UIActionCallResultPayload() {
        A2UIActionCallResultPayload ok = new A2UIActionCallResultPayload(true, "reply text");
        assertTrue(ok.success);
        assertEquals("reply text", ok.result);
        assertTrue(ok.succeeded());

        A2UIActionCallResultPayload fail = new A2UIActionCallResultPayload(false, null);
        assertFalse(fail.success);
        assertFalse(fail.succeeded());
    }

    @Test
    public void testMessageTypeA2UIPayloadBindings() {
        // 6 种 a2ui 类型都注册了正确的 payload class
        assertEquals(A2UIDataPayload.class, MessageType.A2UI_DATA.payloadType());
        assertEquals(A2UIDataResultPayload.class, MessageType.A2UI_DATA_RESULT.payloadType());
        assertEquals(A2UIUpdateDataModelPayload.class, MessageType.A2UI_UPDATE_DATA_MODEL.payloadType());
        assertEquals(A2UIUpdateDataModelResultPayload.class, MessageType.A2UI_UPDATE_DATA_MODEL_RESULT.payloadType());
        assertEquals(A2UIActionCallPayload.class, MessageType.A2UI_ACTION_CALL.payloadType());
        assertEquals(A2UIActionCallResultPayload.class, MessageType.A2UI_ACTION_CALL_RESULT.payloadType());

        // wire 名字
        assertEquals("a2ui_data", MessageType.A2UI_DATA.wireName());
        assertEquals("a2ui_action_call", MessageType.A2UI_ACTION_CALL.wireName());
    }

    @Test
    public void testRegisterPayloadSupportsA2UIDefaultFalse() {
        RegisterPayload rp = new RegisterPayload();
        assertFalse("supportsA2UI 默认应为 false", rp.supportsA2UI);
    }

    @Test
    public void testRegisterPayloadSupportsA2UISetTrue() {
        RegisterPayload rp = new RegisterPayload();
        rp.supportsA2UI = true;
        assertTrue(rp.supportsA2UI);
    }
}
