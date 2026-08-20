package com.ultrabar.plugin.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Reads {@link Envelope} as a bean and maps {@code payload} using {@link MessageType}.
 */
public final class EnvelopeDeserializer extends JsonDeserializer<Envelope> {
    @Override
    public Envelope deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode root = codec.readTree(parser);
        Envelope envelope = new Envelope();
        envelope.setType(MessageType.fromWire(text(root, "type")));
        envelope.setRequestId(text(root, "requestId"));
        envelope.setTimestamp(text(root, "timestamp"));
        envelope.setSessionId(text(root, "sessionId"));
        envelope.setAuth(text(root, "auth"));
        if (root.hasNonNull("protocol")) {
            envelope.setProtocol(codec.treeToValue(root.get("protocol"), Protocol.class));
        }
        MessageType type = envelope.getType();
        if (type != null && root.has("payload") && !root.get("payload").isNull()) {
            envelope.setPayload(codec.treeToValue(root.get("payload"), type.payloadType()));
        }
        return envelope;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }
}
