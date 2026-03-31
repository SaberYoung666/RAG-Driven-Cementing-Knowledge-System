package org.swpu.backend.modules.logging;

public final class LogConstants {
    public static final String SCOPE_PRIVATE = "PRIVATE";
    public static final String SCOPE_SYSTEM = "SYSTEM";

    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";
    public static final String LEVEL_DEBUG = "DEBUG";

    public static final String SOURCE_REQUEST = "REQUEST";
    public static final String SOURCE_BUSINESS = "BUSINESS";
    public static final String SOURCE_EXCEPTION = "EXCEPTION";
    public static final String SOURCE_ASYNC = "ASYNC";

    private LogConstants() {
    }
}
