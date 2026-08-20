package com.ultrabar.plugin.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorInfo {
    public String code;
    public String message;
    public Boolean retryable;
    public Map<String, Object> details;
    public String timestamp;

    public ErrorInfo() {}

    public static ErrorInfo of(String code, String message, boolean retryable, Map<String, Object> details) {
        ErrorInfo error = new ErrorInfo();
        error.code = code;
        error.message = message;
        error.retryable = retryable;
        error.details = details;
        error.timestamp = Timestamps.utcNow();
        return error;
    }
}
