package com.ultrabar.plugin.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Correlates outbound requestId values to typed futures.
 */
public final class RequestTable {
    private final ScheduledExecutorService scheduler;
    private final ObjectMapper mapper;
    private final Map<String, Entry<?>> pending = new ConcurrentHashMap<String, Entry<?>>();

    public RequestTable(ScheduledExecutorService scheduler, ObjectMapper mapper) {
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    public <T> CompletableFuture<T> register(final String requestId, final Class<T> type, long timeoutMs) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        Entry<?> previous = pending.put(requestId, new Entry<T>(type, future));
        if (previous != null) {
            previous.fail(new IllegalStateException("replaced pending request " + requestId));
        }
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                Entry<?> timedOut = pending.remove(requestId);
                if (timedOut != null) {
                    timedOut.fail(new TimeoutException("request timed out: " + requestId));
                }
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        return future;
    }

    public boolean complete(String requestId, JsonNode payload) {
        if (requestId == null) {
            return false;
        }
        Entry<?> entry = pending.remove(requestId);
        if (entry == null) {
            return false;
        }
        entry.complete(mapper, payload);
        return true;
    }

    public void fail(String requestId, Throwable error) {
        Entry<?> entry = pending.remove(requestId);
        if (entry != null) {
            entry.fail(error);
        }
    }

    public void failAll(Throwable error) {
        for (String id : pending.keySet()) {
            Entry<?> removed = pending.remove(id);
            if (removed != null) {
                removed.fail(error);
            }
        }
    }

    private static final class Entry<T> {
        private final Class<T> type;
        private final CompletableFuture<T> future;

        Entry(Class<T> type, CompletableFuture<T> future) {
            this.type = type;
            this.future = future;
        }

        void complete(ObjectMapper mapper, JsonNode payload) {
            try {
                T converted = (type == JsonNode.class)
                        ? type.cast(payload)
                        : mapper.treeToValue(payload, type);
                future.complete(converted);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }

        void fail(Throwable error) {
            future.completeExceptionally(error);
        }
    }
}
