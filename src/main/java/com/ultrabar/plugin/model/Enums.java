package com.ultrabar.plugin.model;

final class Enums {
    private Enums() {}

    static <T extends Enum<T> & WireEnum> T fromWire(T[] values, String wireName) {
        if (wireName == null) {
            return null;
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i].wireName().equals(wireName)) {
                return values[i];
            }
        }
        return null;
    }
}
