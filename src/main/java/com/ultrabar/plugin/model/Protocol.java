package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Protocol {
    public static final int PLUGIN_VERSION = 2;

    private Integer version;

    public Protocol() {}

    public Protocol(Integer version) {

        this.version = version;
    }

    public static Protocol current() {
        return new Protocol(PLUGIN_VERSION);
    }

    /** @deprecated use {@link #current()} */
    public static Protocol pluginV1() {
        return current();
    }
    public Integer getVersion() {
        return version;
    }
    public void setVersion(Integer version) {
        this.version = version;
    }
}
