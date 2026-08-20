package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Protocol {
    public static final String PLUGIN_NAME = "ultrabar.plugin";
    public static final int PLUGIN_VERSION = 2;

    private String name;
    private Integer version;

    public Protocol() {}

    public Protocol(String name, Integer version) {
        this.name = name;
        this.version = version;
    }

    public static Protocol current() {
        return new Protocol(PLUGIN_NAME, PLUGIN_VERSION);
    }

    /** @deprecated use {@link #current()} */
    public static Protocol pluginV1() {
        return current();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
