package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescribeResultPayload extends ReplyPayload {
    public Map<String, Object> details;
    public String actionId;
    public List<ParameterSpec> parameters;

    public DescribeResultPayload() {}

    public DescribeResultPayload(Boolean success, Map<String, Object> details, ErrorInfo error) {
        this.success = success;
        this.details = details;
        this.error = error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ParameterSpec {
        public String id;
        public ParameterType type;
        public String name;
        public Boolean required;
        public String placeholder;
        public Integer maxLength;
        public OptionSpec options;
        public List<String> dependsOn;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OptionSpec {
        public OptionProvider provider;
        public Boolean searchable;
        public List<Label> items;
    }
}
