package com.university.docassistant.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force guard for course invitation codes: at most {@value #MAX_FAILURES} wrong codes per user within
 * {@link #WINDOW}; after that every join attempt of that user is refused until the window ends. In memory: a
 * restart clears it, and each server instance counts on its own, which is enough against guessing 128-bit codes
 * through the UI or a script.
 */
@Component
public class JoinAttemptLimiter {

    public static final int MAX_FAILURES = 10;
    public static final Duration WINDOW = Duration.ofMinutes(10);

    private record Window(Instant start, int failures) {
    }

    private final Map<Long, Window> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public JoinAttemptLimiter() {
        this(Clock.systemUTC());
    }

    public JoinAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Seconds until the user may try again, or 0 when they are not blocked. */
    public long blockedForSeconds(Long userId) {
        Window window = current(userId);
        if (window == null || window.failures() < MAX_FAILURES) {
            return 0;
        }
        return Math.max(1, Duration.between(clock.instant(), window.start().plus(WINDOW)).toSeconds());
    }

    public void recordFailure(Long userId) {
        Instant now = clock.instant();
        failures.compute(userId, (id, window) -> window == null || expired(window, now)
                ? new Window(now, 1)
                : new Window(window.start(), window.failures() + 1));
    }

    private Window current(Long userId) {
        Instant now = clock.instant();
        // Expired windows are dropped on access, so the map only holds users with recent failures.
        return failures.computeIfPresent(userId, (id, window) -> expired(window, now) ? null : window);
    }

    private static boolean expired(Window window, Instant now) {
        return !now.isBefore(window.start().plus(WINDOW));
    }
}
