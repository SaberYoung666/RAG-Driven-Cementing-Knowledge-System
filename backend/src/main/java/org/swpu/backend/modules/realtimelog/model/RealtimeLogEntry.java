package org.swpu.backend.modules.realtimelog.model;

public record RealtimeLogEntry(
        String type,
        String source,
        String level,
        String logger,
        String thread,
        String message,
        String timestamp,
        String details
) {
    public static RealtimeLogEntry heartbeat(String source, String timestamp) {
        return new RealtimeLogEntry("heartbeat", source, "INFO", source, null, "heartbeat", timestamp, null);
    }
}
