package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RagRateLimiter {
    private final RagProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> requests = new ConcurrentHashMap<>();

    @Autowired
    public RagRateLimiter(RagProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RagRateLimiter(RagProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void check(String clientKey) {
        int limit = Math.max(1, properties.getRequestsPerMinute());
        Instant cutoff = clock.instant().minusSeconds(60);
        Deque<Instant> window = requests.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.removeFirst();
            }
            if (window.size() >= limit) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Bạn đã gửi quá nhiều câu hỏi. Vui lòng thử lại sau một phút.");
            }
            window.addLast(clock.instant());
        }
        if (requests.size() > 10_000) {
            requests.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
                }
            });
        }
    }
}
