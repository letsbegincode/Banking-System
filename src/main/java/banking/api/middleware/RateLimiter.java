package banking.api.middleware;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket style rate limiter that keeps track of request timestamps per client key.
 */
public final class RateLimiter {
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requestWindows;

    public RateLimiter(int maxRequests, Duration window) {
        this(maxRequests, window, Clock.systemUTC());
    }

    RateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        this.maxRequests = maxRequests;
        this.window = Objects.requireNonNull(window, "window");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.requestWindows = new ConcurrentHashMap<>();
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        Deque<Instant> deque = requestWindows.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            prune(deque, now.minus(window));
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private void prune(Deque<Instant> deque, Instant threshold) {
        while (!deque.isEmpty()) {
            Instant head = deque.peekFirst();
            if (head.isBefore(threshold)) {
                deque.removeFirst();
            } else {
                break;
            }
        }
    }
}
