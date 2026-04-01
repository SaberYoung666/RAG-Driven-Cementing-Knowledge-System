package org.swpu.backend.modules.realtimeconsole.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.swpu.backend.modules.realtimeconsole.model.RealtimeConsoleEntry;
import org.swpu.backend.modules.realtimeconsole.support.RealtimeConsoleBridge;

@Service
public class RealtimeConsoleHub {
    private static final int MAX_QUEUE_SIZE = 2000;

    private final Set<LinkedBlockingDeque<RealtimeConsoleEntry>> subscribers = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        RealtimeConsoleBridge.register(this::publish);
    }

    @PreDestroy
    public void destroy() {
        RealtimeConsoleBridge.clear();
        subscribers.clear();
    }

    public LinkedBlockingDeque<RealtimeConsoleEntry> subscribe() {
        LinkedBlockingDeque<RealtimeConsoleEntry> queue = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);
        subscribers.add(queue);
        return queue;
    }

    public void unsubscribe(LinkedBlockingDeque<RealtimeConsoleEntry> queue) {
        if (queue == null) {
            return;
        }
        subscribers.remove(queue);
        queue.clear();
    }

    public RealtimeConsoleEntry heartbeat() {
        return RealtimeConsoleEntry.heartbeat("backend");
    }

    public RealtimeConsoleEntry poll(LinkedBlockingDeque<RealtimeConsoleEntry> queue, long timeout, TimeUnit unit)
            throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public void publish(RealtimeConsoleEntry entry) {
        for (LinkedBlockingDeque<RealtimeConsoleEntry> queue : subscribers) {
            if (!queue.offerLast(entry)) {
                queue.pollFirst();
                queue.offerLast(entry);
            }
        }
    }
}
