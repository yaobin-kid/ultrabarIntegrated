package com.ultrabar.plugin.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * agenui 协议载荷的构造与序列化单元测试。
 */
public class AGenUIProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testAGenUIDataPayloadFields() {
        AGenUIDataPayload p = new AGenUIDataPayload("{\"status\":\"playing\",\"volume\":80}");
        assertEquals("{\"status\":\"playing\",\"volume\":80}", p.data);
        assertNotNull(p.timestamp);
        assertTrue(p.timestamp > 0);
    }

    @Test
    public void testAGenUIDataPayloadJson() throws Exception {
        AGenUIDataPayload p = new AGenUIDataPayload("{\"status\":\"playing\"}");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("playing"));

        AGenUIDataPayload round = mapper.readValue(json, AGenUIDataPayload.class);
        assertEquals("{\"status\":\"playing\"}", round.data);
    }

    @Test
    public void testAGenUIUpdateDataModelPayloadDefaultIncremental() {
        AGenUIUpdateDataModelPayload p = new AGenUIUpdateDataModelPayload("{\"phase\":\"thinking\"}");
        assertEquals("{\"phase\":\"thinking\"}", p.dataModel);
        assertFalse(p.incremental);
        assertNotNull(p.timestamp);
    }

    @Test
    public void testAGenUIUpdateDataModelPayloadIncrementalTrue() {
        AGenUIUpdateDataModelPayload p = new AGenUIUpdateDataModelPayload();
        p.dataModel = "{\"phase\":\"acting\"}";
        p.incremental = true;
        p.timestamp = 123L;

        assertEquals("{\"phase\":\"acting\"}", p.dataModel);
        assertTrue(p.incremental);
        assertEquals(Long.valueOf(123L), p.timestamp);
    }

    @Test
    public void testAGenUIUpdateDataModelPayloadJson() throws Exception {
        AGenUIUpdateDataModelPayload p = new AGenUIUpdateDataModelPayload("{\"phase\":\"thinking\"}");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("thinking"));

        AGenUIUpdateDataModelPayload round = mapper.readValue(json, AGenUIUpdateDataModelPayload.class);
        assertEquals("{\"phase\":\"thinking\"}", round.dataModel);
    }

    @Test
    public void testAGenUIActionCallPayloadFields() {
        String argsJson = "{\"prompt\":\"hello\"}";

        AGenUIActionCallPayload p = new AGenUIActionCallPayload("agenui_chat", argsJson);
        assertEquals("agenui_chat", p.functionName);
        assertEquals(argsJson, p.args);
    }

    @Test
    public void testAGenUIActionCallPayloadJson() throws Exception {
        AGenUIActionCallPayload p = new AGenUIActionCallPayload("agenui_reset", null);
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("agenui_reset"));

        AGenUIActionCallPayload round = mapper.readValue(json, AGenUIActionCallPayload.class);
        assertEquals("agenui_reset", round.functionName);
        assertNull(round.args);
    }

    @Test
    public void testAGenUIActionCallResultPayload() {
        AGenUIActionCallResultPayload ok = new AGenUIActionCallResultPayload(true, "reply text");
        assertTrue(ok.success);
        assertEquals("reply text", ok.result);
        assertTrue(ok.succeeded());

        AGenUIActionCallResultPayload fail = new AGenUIActionCallResultPayload(false, null);
        assertFalse(fail.success);
        assertFalse(fail.succeeded());
    }

    @Test
    public void testMessageTypeAGenUIPayloadBindings() {
        // 6 种 agenui 类型都注册了正确的 payload class
        assertEquals(AGenUIDataPayload.class, MessageType.AGENUI_DATA.payloadType());
        assertEquals(AGenUIDataResultPayload.class, MessageType.AGENUI_DATA_RESULT.payloadType());
        assertEquals(AGenUIUpdateDataModelPayload.class, MessageType.AGENUI_UPDATE_DATA_MODEL.payloadType());
        assertEquals(AGenUIUpdateDataModelResultPayload.class, MessageType.AGENUI_UPDATE_DATA_MODEL_RESULT.payloadType());
        assertEquals(AGenUIActionCallPayload.class, MessageType.AGENUI_ACTION_CALL.payloadType());
        assertEquals(AGenUIActionCallResultPayload.class, MessageType.AGENUI_ACTION_CALL_RESULT.payloadType());

        // wire 名字
        assertEquals("agenui_data", MessageType.AGENUI_DATA.wireName());
        assertEquals("agenui_action_call", MessageType.AGENUI_ACTION_CALL.wireName());
    }

    @Test
    public void testRegisterPayloadSupportsAGenUIDefaultFalse() {
        RegisterPayload rp = new RegisterPayload();
        assertFalse("supportsAGenUI 默认应为 false", rp.supportsAGenUI);
    }

    @Test
    public void testRegisterPayloadSupportsAGenUISetTrue() {
        RegisterPayload rp = new RegisterPayload();
        rp.supportsAGenUI = true;
        assertTrue(rp.supportsAGenUI);
    }
}
