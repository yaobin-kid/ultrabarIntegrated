package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.Protocol;

import java.time.Instant;

public final class Envelopes {
    static final Protocol PROTOCOL = new Protocol("ultrabar.plugin", 1);

    private Envelopes() {}

    static Envelope create(String type, String requestId, Object payload) {
        Envelope env = new Envelope();
        env.type = type;
        env.requestId = requestId;
        env.timestamp = Instant.now().toString();
        env.protocol = PROTOCOL;
        env.payload = payload;
        return env;
    }
}
