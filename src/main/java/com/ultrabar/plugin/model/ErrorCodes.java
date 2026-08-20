package com.ultrabar.plugin.model;

public final class ErrorCodes {
    public static final String INVALID_PAYLOAD = "INVALID_PAYLOAD";
    public static final String NO_HANDLER = "NO_HANDLER";
    public static final String HANDLER_EXCEPTION = "HANDLER_EXCEPTION";
    public static final String MISSING_PARAM = "MISSING_PARAM";
    public static final String MISSING_PACKAGE = "MISSING_PACKAGE";
    public static final String NO_SESSION = "NO_SESSION";
    public static final String UNKNOWN_ACTION = "UNKNOWN_ACTION";
    public static final String AMBIGUOUS_ACTION = "AMBIGUOUS_ACTION";
    public static final String AUTH_FAILED = "AUTH_FAILED";

    private ErrorCodes() {}
}
