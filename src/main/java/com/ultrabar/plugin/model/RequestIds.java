package com.ultrabar.plugin.model;

import java.util.UUID;

public final class RequestIds {
    private RequestIds() {}

    /**
     * Unique correlation id for one request/response pair. Callers must not reuse it.
     */
    public static String next() {
        return UUID.randomUUID().toString();
    }
}
