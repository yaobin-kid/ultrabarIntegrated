package com.ultrabar.plugin.internal;

import com.ultrabar.plugin.model.Envelope;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class EnvelopeClient {
    private static final long REQUEST_TIMEOUT_MS = 30_000L;

    private final LineConnection connection;
    private final RequestTable requests;
    private final AtomicLong idGen = new AtomicLong(1);

    public EnvelopeClient(LineConnection connection, RequestTable requests) {
        this.connection = connection;
        this.requests = requests;
    }

    <T> CompletableFuture<T> request(String type, Object payload, Class<T> responseType) {
        String requestId = nextRequestId();
        CompletableFuture<T> future = requests.register(requestId, responseType, REQUEST_TIMEOUT_MS);
        try {
            send(Envelopes.create(type, requestId, payload));
        } catch (RuntimeException e) {
            requests.fail(requestId, e);
            throw e;
        }
        return future;
    }

    void sendOneWay(String type, Object payload) {
        send(Envelopes.create(type, nextRequestId(), payload));
    }

    void send(Envelope envelope) {
        try {
            connection.sendJson(envelope);
        } catch (RuntimeException e) {
            if (envelope.requestId != null) {
                requests.fail(envelope.requestId, e);
            }
            throw e;
        }
    }

    private String nextRequestId() {
        return "req-" + idGen.getAndIncrement();
    }
}
