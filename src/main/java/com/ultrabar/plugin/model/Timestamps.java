package com.ultrabar.plugin.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;

public final class Timestamps {
    private Timestamps() {}

    public static String utcNow() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
