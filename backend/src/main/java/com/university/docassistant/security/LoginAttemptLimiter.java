package com.university.docassistant.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Password brute-force guard, in memory:
 * <ul>
 *   <li>per (email, client IP): {@value #PAIR_MAX_FAILURES} failures within {@link #WINDOW} block that pair for
 *       {@link #BLOCK};</li>
 *   <li>per client IP: {@value #IP_MAX_FAILURES} failures within {@link #WINDOW}, over any emails, block the IP for
 *       {@link #BLOCK}.</li>
 * </ul>
 * An email is never blocked on its own: otherwise anyone could lock a real user out from anywhere. A successful
 * login clears its pair (not the IP). Entries expire by time and are swept at most every {@link #SWEEP_EVERY}; a
 * restart clears everything, and each server instance counts on its own.
 */
@Component
public class LoginAttemptLimiter {

    public static final int PAIR_MAX_FAILURES = 5;
    public static final int IP_MAX_FAILURES = 30;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration BLOCK = Duration.ofMinutes(15);
    static final Duration SWEEP_EVERY = Duration.ofMinutes(5);

    /** What a failure led to, so the caller can audit lockouts (and only lockouts). */
    public enum Outcome {
        COUNTED, PAIR_LOCKED, IP_LOCKED
    }

    /** Failure times within the window, and the end of a block if one is active. */
    private static final class Counter {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;
    }

    private final Map<String, Counter> pairs = new ConcurrentHashMap<>();
    private final Map<String, Counter> ips = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile Instant lastSweep;

    @Autowired
    public LoginAttemptLimiter() {
        this(Clock.systemUTC());
    }

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
        this.lastSweep = clock.instant();
    }

    /** Seconds until this email may be tried again from this IP; 0 when neither the pair nor the IP is blocked. */
    public long blockedForSeconds(String email, String ip) {
        Instant now = clock.instant();
        return Math.max(remaining(pairs.get(pairKey(email, ip)), now), remaining(ips.get(ip), now));
    }

    /** Counts a failed login of this email from this IP. */
    public Outcome recordFailure(String email, String ip) {
        Instant now = clock.instant();
        sweepIfDue(now);
        boolean pairLocked = fail(pairs.computeIfAbsent(pairKey(email, ip), key -> new Counter()), now, PAIR_MAX_FAILURES);
        boolean ipLocked = fail(ips.computeIfAbsent(ip, key -> new Counter()), now, IP_MAX_FAILURES);
        return ipLocked ? Outcome.IP_LOCKED : pairLocked ? Outcome.PAIR_LOCKED : Outcome.COUNTED;
    }

    /** A correct password: this email starts from zero again on this IP. The IP's own count is kept. */
    public void recordSuccess(String email, String ip) {
        pairs.remove(pairKey(email, ip));
    }

    /** True when this failure started a block. */
    private static boolean fail(Counter counter, Instant now, int max) {
        synchronized (counter) {
            dropOld(counter, now);
            if (counter.blockedUntil != null && now.isBefore(counter.blockedUntil)) {
                return false; // Already blocked; requests are refused before reaching here, this is a race.
            }
            counter.failures.addLast(now);
            if (counter.failures.size() >= max) {
                counter.failures.clear();
                counter.blockedUntil = now.plus(BLOCK);
                return true;
            }
            return false;
        }
    }

    private static long remaining(Counter counter, Instant now) {
        if (counter == null) {
            return 0;
        }
        synchronized (counter) {
            if (counter.blockedUntil == null || !now.isBefore(counter.blockedUntil)) {
                return 0;
            }
            return Math.max(1, Duration.between(now, counter.blockedUntil).toSeconds());
        }
    }

    private static void dropOld(Counter counter, Instant now) {
        Instant oldest = now.minus(WINDOW);
        while (!counter.failures.isEmpty() && !counter.failures.peekFirst().isAfter(oldest)) {
            counter.failures.removeFirst();
        }
        if (counter.blockedUntil != null && !now.isBefore(counter.blockedUntil)) {
            counter.blockedUntil = null;
        }
    }

    /** Removes counters with no recent failure and no active block, so the maps only hold the last ~15 minutes. */
    private void sweepIfDue(Instant now) {
        if (Duration.between(lastSweep, now).compareTo(SWEEP_EVERY) < 0) {
            return;
        }
        lastSweep = now;
        for (Map<String, Counter> map : java.util.List.of(pairs, ips)) {
            map.entrySet().removeIf(entry -> {
                Counter counter = entry.getValue();
                synchronized (counter) {
                    dropOld(counter, now);
                    return counter.failures.isEmpty() && counter.blockedUntil == null;
                }
            });
        }
    }

    int trackedPairs() {
        return pairs.size();
    }

    private static String pairKey(String email, String ip) {
        return (email == null ? "" : email.trim().toLowerCase(Locale.ROOT)) + "|" + ip;
    }
}
