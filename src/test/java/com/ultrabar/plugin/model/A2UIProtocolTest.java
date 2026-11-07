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
    public void testA2UIDataPayloadFields() {
        A2UIDataPayload p = new A2UIDataPayload("{\"status\":\"playing\",\"volume\":80}");
        assertEquals("{\"status\":\"playing\",\"volume\":80}", p.data);
        assertNotNull(p.timestamp);
        assertTrue(p.timestamp > 0);
    }

    @Test
    public void testA2UIDataPayloadJson() throws Exception {
        A2UIDataPayload p = new A2UIDataPayload("{\"status\":\"playing\"}");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("playing"));

        A2UIDataPayload round = mapper.readValue(json, A2UIDataPayload.class);
        assertEquals("{\"status\":\"playing\"}", round.data);
    }

    @Test
    public void testA2UIUpdateDataModelPayloadDefaultIncremental() {
        A2UIUpdateDataModelPayload p = new A2UIUpdateDataModelPayload("{\"phase\":\"thinking\"}");
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
        A2UIUpdateDataModelPayload p = new A2UIUpdateDataModelPayload("{\"phase\":\"thinking\"}");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("thinking"));

        A2UIUpdateDataModelPayload round = mapper.readValue(json, A2UIUpdateDataModelPayload.class);
        assertEquals("{\"phase\":\"thinking\"}", round.dataModel);
    }

    @Test
    public void testA2UIActionCallPayloadFields() {
        String argsJson = "{\"prompt\":\"hello\"}";

        A2UIActionCallPayload p = new A2UIActionCallPayload("a2ui_chat", argsJson);
        assertEquals("a2ui_chat", p.actionName);
        assertEquals(argsJson, p.context);
    }

    @Test
    public void testA2UIActionCallPayloadJson() throws Exception {
        A2UIActionCallPayload p = new A2UIActionCallPayload("a2ui_reset", null);
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("a2ui_reset"));

        A2UIActionCallPayload round = mapper.readValue(json, A2UIActionCallPayload.class);
        assertEquals("a2ui_reset", round.actionName);
        assertNull(round.context);
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
