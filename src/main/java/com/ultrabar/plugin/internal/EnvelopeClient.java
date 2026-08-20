package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.Envelope;
import com.ultrabar.plugin.model.MessageType;
import com.ultrabar.plugin.model.Payload;
import com.ultrabar.plugin.model.RequestIds;

import java.util.concurrent.CompletableFuture;

public final class EnvelopeClient {
    private static final long REQUEST_TIMEOUT_MS = 30_000L;

    private final LineConnection connection;
    private final RequestTable requests;
    private final SessionState session;

    public EnvelopeClient(LineConnection connection, RequestTable requests, SessionState session) {
        this.connection = connection;
        this.requests = requests;
        this.session = session;
    }

    <T> CompletableFuture<T> request(MessageType type, Payload payload, Class<T> responseType) {
        String requestId = RequestIds.next();
        CompletableFuture<T> future = requests.register(requestId, responseType, REQUEST_TIMEOUT_MS);
        try {
            send(Envelope.of(type, requestId, payload));
        } catch (RuntimeException e) {
            requests.fail(requestId, e);
            throw e;
        }
        return future;
    }

    public void sendOneWay(MessageType type, Payload payload) {
        send(Envelope.of(type, RequestIds.next(), payload));
    }

    void send(Envelope envelope) {
        if (session.isOpen()) {
            envelope.withSession(session.sessionId(), session.sessionToken());
        }
        try {
            connection.sendJson(envelope);
        } catch (RuntimeException e) {
            if (envelope.getRequestId() != null) {
                requests.fail(envelope.getRequestId(), e);
            }
            throw e;
        }
    }
}
