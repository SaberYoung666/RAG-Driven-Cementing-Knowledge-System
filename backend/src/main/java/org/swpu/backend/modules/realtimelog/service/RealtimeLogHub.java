package org.swpu.backend.modules.realtimelog.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.swpu.backend.modules.realtimelog.model.RealtimeLogEntry;
import org.swpu.backend.modules.realtimelog.support.RealtimeLogBridge;

@Service
public class RealtimeLogHub {
    private static final int MAX_QUEUE_SIZE = 500;
    private final Set<LinkedBlockingDeque<RealtimeLogEntry>> subscribers = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        RealtimeLogBridge.register(this::publish);
    }

    @PreDestroy
    public void destroy() {
        RealtimeLogBridge.clear();
        subscribers.clear();
    }

    public LinkedBlockingDeque<RealtimeLogEntry> subscribe() {
        LinkedBlockingDeque<RealtimeLogEntry> queue = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);
        subscribers.add(queue);
        return queue;
    }

    public void unsubscribe(LinkedBlockingDeque<RealtimeLogEntry> queue) {
        if (queue == null) {
            return;
        }
        subscribers.remove(queue);
        queue.clear();
    }

    public RealtimeLogEntry heartbeat() {
        return RealtimeLogEntry.heartbeat("backend", Instant.now().toString());
    }

    public RealtimeLogEntry poll(LinkedBlockingDeque<RealtimeLogEntry> queue, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    public void publish(RealtimeLogEntry entry) {
        for (LinkedBlockingDeque<RealtimeLogEntry> queue : subscribers) {
            if (!queue.offerLast(entry)) {
                queue.pollFirst();
                queue.offerLast(entry);
            }
        }
    }
}
