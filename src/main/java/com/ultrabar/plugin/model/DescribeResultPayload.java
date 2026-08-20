package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescribeResultPayload {
    public Boolean success;
    public Map<String, Object> details;
    public ErrorInfo error;

    public DescribeResultPayload() {
    }

    public DescribeResultPayload(Boolean success, Map<String, Object> details, ErrorInfo error) {
        this.success = success;
        this.details = details;
        this.error = error;
    }


    public String actionId;

    public List<Parameters> parameters;

    public static class Parameters {

        public String id;
        public String type;
        public String name;
        public boolean required;
        public String placeholder;
        public int maxLength;
        public Options options;
        public List<String> dependsOn; //依赖
    }

    public static class Options {
        public String provider; //remote ，static
        public boolean searchable;
        public List<Item> items;
    }


}
