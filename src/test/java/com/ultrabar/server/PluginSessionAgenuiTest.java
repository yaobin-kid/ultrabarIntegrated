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
public class PluginSessionAGenUITest {

    private PluginSession newSession(boolean supportsAGenUI) {
        RegisterPayload rp = new RegisterPayload();
        rp.name = "TestPlugin";
        rp.packageName = "com.test.plugin";
        rp.supportsAGenUI = supportsAGenUI;
        Channel channel = mock(Channel.class);
        when(channel.isActive()).thenReturn(true);
        return new PluginSession("TestPlugin", "com.test.plugin", "session_1", "token_1", rp, channel, 100);
    }

    @Test
    public void testSupportsAGenUITrue() {
        PluginSession s = newSession(true);
        assertTrue(s.supportsAGenUI());
    }

    @Test
    public void testSupportsAGenUIFalse() {
        PluginSession s = newSession(false);
        assertFalse(s.supportsAGenUI());
    }

    @Test
    public void testAGenUIDataEmptyInitially() {
        PluginSession s = newSession(true);
        assertNull(s.aGenUIData());
    }

    @Test
    public void testSetAndGetAgenuiData() {
        PluginSession s = newSession(true);
        s.setAGenUIData("{\"status\":\"playing\"}");
        assertEquals("{\"status\":\"playing\"}", s.aGenUIData());
    }

    @Test
    public void testSetAGenUIDataNullIgnored() {
        PluginSession s = newSession(true);
        s.setAGenUIData(null);
        assertNull(s.aGenUIData());
    }

    @Test
    public void testSetAGenUIDataOverwrite() {
        PluginSession s = newSession(true);
        s.setAGenUIData("{\"status\":\"playing\"}");
        s.setAGenUIData("{\"status\":\"paused\"}");
        assertEquals("{\"status\":\"paused\"}", s.aGenUIData());
    }

    @Test
    public void testAGenUIDataModelEmptyInitially() {
        PluginSession s = newSession(true);
        assertNull(s.aGenUIDataModel());
    }

    @Test
    public void testSetAndGetAGenUIDataModel() {
        PluginSession s = newSession(true);
        s.setAGenUIDataModel("{\"phase\":\"thinking\"}");
        assertEquals("{\"phase\":\"thinking\"}", s.aGenUIDataModel());
    }

    @Test
    public void testSetAGenUIDataModelNullIgnored() {
        PluginSession s = newSession(true);
        s.setAGenUIDataModel(null);
        assertNull(s.aGenUIDataModel());
    }

    @Test
    public void testSetAGenUIDataModelOverwrite() {
        PluginSession s = newSession(true);
        s.setAGenUIDataModel("{\"phase\":\"thinking\"}");
        s.setAGenUIDataModel("{\"phase\":\"acting\"}");
        assertEquals("{\"phase\":\"acting\"}", s.aGenUIDataModel());
    }
}
