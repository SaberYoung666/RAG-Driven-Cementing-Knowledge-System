package org.swpu.backend.modules.realtimeconsole.support;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import org.swpu.backend.modules.realtimeconsole.model.RealtimeConsoleEntry;

public final class RealtimeConsoleBridge {
    private static final int BUFFER_LIMIT = 500;
    private static final Object LOCK = new Object();

    private static final ArrayDeque<RealtimeConsoleEntry> PENDING = new ArrayDeque<>();
    private static Consumer<RealtimeConsoleEntry> sink;

    private RealtimeConsoleBridge() {
    }

    public static void register(Consumer<RealtimeConsoleEntry> consumer) {
        synchronized (LOCK) {
            sink = consumer;
            while (!PENDING.isEmpty()) {
                sink.accept(PENDING.removeFirst());
            }
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            sink = null;
            PENDING.clear();
        }
    }

    public static void publish(RealtimeConsoleEntry entry) {
        if (entry == null) {
            return;
        }
        synchronized (LOCK) {
            if (sink != null) {
                sink.accept(entry);
                return;
            }
            if (PENDING.size() >= BUFFER_LIMIT) {
                PENDING.removeFirst();
            }
            PENDING.addLast(entry);
        }
    }
}
