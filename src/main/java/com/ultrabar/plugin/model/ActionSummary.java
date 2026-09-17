package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionSummary {
    public String actionId;
    public String name;
    public String description;
    public final int features;

    public ActionSummary(Features... features) {
        Objects.requireNonNull(features, "features must not be null");
        int combined = 0;
        for (Features f : features) {
            combined |= f.code();
        }
        this.features = combined;
    }

    public ActionSummary() {
        this(Features.CALL);
    }


    public boolean supports(Features feature) {
        return (this.features & feature.code()) != 0;
    }
    public boolean supportsAll(Features... features) {
        for (Features f : features) {
            if (!supports(f)) {
                return false;
            }
        }
        return true;
    }
}


