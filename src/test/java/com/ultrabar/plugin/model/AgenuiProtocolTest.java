package com.ultrabar.plugin.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * agenui 协议载荷的构造与序列化单元测试。
 */
public class AgenuiProtocolTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testAgenuiDataPayloadFields() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("status", "playing");
        data.put("volume", 80);

        AgenuiDataPayload p = new AgenuiDataPayload("audio_event", data);
        assertEquals("audio_event", p.dataType);
        assertEquals(data, p.data);
        assertNotNull(p.timestamp);
        assertTrue(p.timestamp > 0);
    }

    @Test
    public void testAgenuiDataPayloadJson() throws Exception {
        AgenuiDataPayload p = new AgenuiDataPayload("audio_event", "playing");
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("audio_event"));
        assertTrue(json.contains("playing"));

        AgenuiDataPayload round = mapper.readValue(json, AgenuiDataPayload.class);
        assertEquals("audio_event", round.dataType);
        assertEquals("playing", round.data);
    }

    @Test
    public void testAgenuiUpdateDataModelPayloadDefaultIncremental() {
        AgenuiUpdateDataModelPayload p = new AgenuiUpdateDataModelPayload("agent_state", "thinking");
        assertEquals("agent_state", p.modelType);
        assertEquals("thinking", p.dataModel);
        assertFalse(p.incremental);
        assertNotNull(p.timestamp);
    }

    @Test
    public void testAgenuiUpdateDataModelPayloadIncrementalTrue() {
        AgenuiUpdateDataModelPayload p = new AgenuiUpdateDataModelPayload();
        p.modelType = "agent_state";
        p.dataModel = Map.of("phase", "acting");
        p.incremental = true;
        p.timestamp = 123L;

        assertEquals("agent_state", p.modelType);
        assertTrue(p.incremental);
        assertEquals(Long.valueOf(123L), p.timestamp);
    }

    @Test
    public void testAgenuiUpdateDataModelPayloadJson() throws Exception {
        AgenuiUpdateDataModelPayload p = new AgenuiUpdateDataModelPayload("agent_state", Map.of("phase", "thinking"));
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("agent_state"));
        assertTrue(json.contains("thinking"));

        AgenuiUpdateDataModelPayload round = mapper.readValue(json, AgenuiUpdateDataModelPayload.class);
        assertEquals("agent_state", round.modelType);
    }

    @Test
    public void testAgenuiActionCallPayloadFields() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("prompt", "hello");

        AgenuiActionCallPayload p = new AgenuiActionCallPayload("agenui_chat", params);
        assertEquals("agenui_chat", p.actionId);
        assertEquals(params, p.params);
    }

    @Test
    public void testAgenuiActionCallPayloadJson() throws Exception {
        AgenuiActionCallPayload p = new AgenuiActionCallPayload("agenui_reset", null);
        String json = mapper.writeValueAsString(p);
        assertTrue(json.contains("agenui_reset"));

        AgenuiActionCallPayload round = mapper.readValue(json, AgenuiActionCallPayload.class);
        assertEquals("agenui_reset", round.actionId);
        assertNull(round.params);
    }

    @Test
    public void testAgenuiActionCallResultPayload() {
        AgenuiActionCallResultPayload ok = new AgenuiActionCallResultPayload(true, "reply text");
        assertTrue(ok.success);
        assertEquals("reply text", ok.result);
        assertTrue(ok.succeeded());

        AgenuiActionCallResultPayload fail = new AgenuiActionCallResultPayload(false, null);
        assertFalse(fail.success);
        assertFalse(fail.succeeded());
    }

    @Test
    public void testMessageTypeAgenuiPayloadBindings() {
        // 6 种 agenui 类型都注册了正确的 payload class
        assertEquals(AgenuiDataPayload.class, MessageType.AGENUI_DATA.payloadType());
        assertEquals(AgenuiDataResultPayload.class, MessageType.AGENUI_DATA_RESULT.payloadType());
        assertEquals(AgenuiUpdateDataModelPayload.class, MessageType.AGENUI_UPDATE_DATA_MODEL.payloadType());
        assertEquals(AgenuiUpdateDataModelResultPayload.class, MessageType.AGENUI_UPDATE_DATA_MODEL_RESULT.payloadType());
        assertEquals(AgenuiActionCallPayload.class, MessageType.AGENUI_ACTION_CALL.payloadType());
        assertEquals(AgenuiActionCallResultPayload.class, MessageType.AGENUI_ACTION_CALL_RESULT.payloadType());

        // wire 名字
        assertEquals("agenui_data", MessageType.AGENUI_DATA.wireName());
        assertEquals("agenui_action_call", MessageType.AGENUI_ACTION_CALL.wireName());
    }

    @Test
    public void testRegisterPayloadSupportsAgenuiDefaultFalse() {
        RegisterPayload rp = new RegisterPayload();
        assertFalse("supportsAgenui 默认应为 false", rp.supportsAgenui);
    }

    @Test
    public void testRegisterPayloadSupportsAgenuiSetTrue() {
        RegisterPayload rp = new RegisterPayload();
        rp.supportsAgenui = true;
        assertTrue(rp.supportsAgenui);
    }
}
