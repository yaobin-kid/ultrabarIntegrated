package com.ultrabar.server;

import com.ultrabar.plugin.model.RegisterPayload;
import io.netty.channel.Channel;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * PluginSession 的 agenui 相关单元测试。
 */
public class PluginSessionAgenuiTest {

    private PluginSession newSession(boolean supportsAgenui) {
        RegisterPayload rp = new RegisterPayload();
        rp.name = "TestPlugin";
        rp.packageName = "com.test.plugin";
        rp.supportsAgenui = supportsAgenui;
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        return new PluginSession("TestPlugin", "com.test.plugin", "session_1", "token_1", rp, channel, 100);
    }

    @Test
    public void testSupportsAgenuiTrue() {
        PluginSession s = newSession(true);
        assertTrue(s.supportsAgenui());
    }

    @Test
    public void testSupportsAgenuiFalse() {
        PluginSession s = newSession(false);
        assertFalse(s.supportsAgenui());
    }

    @Test
    public void testAgenuiDataEmptyInitially() {
        PluginSession s = newSession(true);
        Map<String, Object> snapshot = s.agenuiData();
        assertNotNull(snapshot);
        assertTrue(snapshot.isEmpty());
        assertNull(s.agenuiData("any_type"));
    }

    @Test
    public void testSetAndGetAgenuiData() {
        PluginSession s = newSession(true);
        s.setAgenuiData("audio_event", "playing");
        assertEquals("playing", s.agenuiData("audio_event"));

        Map<String, Object> snapshot = s.agenuiData();
        assertEquals(1, snapshot.size());
        assertEquals("playing", snapshot.get("audio_event"));
    }

    @Test
    public void testSetAgenuiDataNullTypeIgnored() {
        PluginSession s = newSession(true);
        s.setAgenuiData(null, "value");
        assertTrue(s.agenuiData().isEmpty());
    }

    @Test
    public void testSetAgenuiDataOverwriteSameType() {
        PluginSession s = newSession(true);
        s.setAgenuiData("audio_event", "playing");
        s.setAgenuiData("audio_event", "paused");
        assertEquals("paused", s.agenuiData("audio_event"));
    }

    @Test
    public void testSetAgenuiDataMultipleTypes() {
        PluginSession s = newSession(true);
        s.setAgenuiData("audio_event", "playing");
        s.setAgenuiData("volume_event", 80);
        Map<String, Object> snapshot = s.agenuiData();
        assertEquals(2, snapshot.size());
    }

    @Test
    public void testAgenuiDataModelEmptyInitially() {
        PluginSession s = newSession(true);
        Map<String, Object> snapshot = s.agenuiDataModels();
        assertNotNull(snapshot);
        assertTrue(snapshot.isEmpty());
        assertNull(s.agenuiDataModel("any_model"));
    }

    @Test
    public void testSetAndGetAgenuiDataModel() {
        PluginSession s = newSession(true);
        s.setAgenuiDataModel("agent_state", "thinking");
        assertEquals("thinking", s.agenuiDataModel("agent_state"));
        assertEquals(1, s.agenuiDataModels().size());
    }

    @Test
    public void testSetAgenuiDataModelNullTypeIgnored() {
        PluginSession s = newSession(true);
        s.setAgenuiDataModel(null, "value");
        assertTrue(s.agenuiDataModels().isEmpty());
    }

    @Test
    public void testSetAgenuiDataModelOverwrite() {
        PluginSession s = newSession(true);
        s.setAgenuiDataModel("agent_state", "thinking");
        s.setAgenuiDataModel("agent_state", "acting");
        assertEquals("acting", s.agenuiDataModel("agent_state"));
    }
}
