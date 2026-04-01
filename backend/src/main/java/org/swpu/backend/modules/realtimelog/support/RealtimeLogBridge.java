package org.swpu.backend.modules.realtimelog.support;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.swpu.backend.modules.realtimelog.model.RealtimeLogEntry;

public final class RealtimeLogBridge {
    private static final AtomicReference<Consumer<RealtimeLogEntry>> CONSUMER = new AtomicReference<>();

    private RealtimeLogBridge() {
    }

    public static void register(Consumer<RealtimeLogEntry> consumer) {
        CONSUMER.set(consumer);
    }

    public static void clear() {
        CONSUMER.set(null);
    }

    public static void publish(RealtimeLogEntry entry) {
        Consumer<RealtimeLogEntry> consumer = CONSUMER.get();
        if (consumer != null && entry != null) {
            consumer.accept(entry);
        }
    }
}
