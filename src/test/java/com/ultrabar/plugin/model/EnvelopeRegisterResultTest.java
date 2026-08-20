package com.ultrabar.plugin.model;

import org.junit.Assert;
import org.junit.Test;

public class EnvelopeRegisterResultTest {
    @Test
    public void registerResultRoundTripKeepsSuccessTrue() throws Exception {
        RegisterResultPayload register = new RegisterResultPayload();
        register.success = true;
        register.sessionId = "sess-1";
        register.sessionToken = "token-1";
        register.heartbeat = new Heartbeat();
        register.heartbeat.interval = 5000;
        register.heartbeat.timeout = 15000;
        ConfigServer configServer = new ConfigServer();
        configServer.port = 81231;
        register.configServer = configServer;

        Envelope outbound = Envelope.of(MessageType.REGISTER_RESULT, "req-1", register);
        String json = Json.mapper().writeValueAsString(outbound);
        Envelope inbound = Json.mapper().readValue(json, Envelope.class);
        RegisterResultPayload parsed = inbound.payloadAs(RegisterResultPayload.class);

        Assert.assertTrue(json.contains("\"success\":true"));
        Assert.assertNotNull(parsed);
        Assert.assertEquals(Boolean.TRUE, parsed.success);
        Assert.assertTrue(parsed.succeeded());
        Assert.assertEquals("sess-1", parsed.sessionId);
        Assert.assertEquals(Integer.valueOf(81231), parsed.configServer.port);
    }
}
