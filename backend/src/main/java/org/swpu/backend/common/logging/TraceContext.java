package org.swpu.backend.common.logging;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {
    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
