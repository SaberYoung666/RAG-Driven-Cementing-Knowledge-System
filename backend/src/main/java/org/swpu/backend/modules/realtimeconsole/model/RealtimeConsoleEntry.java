package org.swpu.backend.modules.realtimeconsole.model;

import java.time.Instant;

public record RealtimeConsoleEntry(
        String type,
        String source,
        String raw,
        String timestamp
) {
    public static RealtimeConsoleEntry chunk(String source, String raw) {
        return new RealtimeConsoleEntry("chunk", source, raw, Instant.now().toString());
    }

    public static RealtimeConsoleEntry heartbeat(String source) {
        return new RealtimeConsoleEntry("heartbeat", source, null, Instant.now().toString());
    }
}
