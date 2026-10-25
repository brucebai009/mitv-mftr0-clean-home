package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.List;

/** Persisted wall-clock budget: at most three repairs per minute, five seconds apart. */
final class HomeRecoveryBudget {
    static final long WINDOW_MS = 60_000, GAP_MS = 5_000;
    static final int MAX_ATTEMPTS = 3;
    static List<Long> recent(String saved, long now) {
        List<Long> result = new ArrayList<>();
        if (saved != null) for (String part : saved.split(",")) {
            try {
                long at = Long.parseLong(part);
                // A clock rollback retains the token until time catches up; it never refills a loop.
                if (now - at < WINDOW_MS) result.add(at);
            } catch (NumberFormatException ignored) { }
        }
        return result;
    }
    static boolean allowed(String saved, long now) {
        List<Long> recent = recent(saved, now);
        if (now < 0 || recent.size() >= MAX_ATTEMPTS) return false;
        for (long at : recent) if (now - at < GAP_MS) return false;
        return true;
    }
    static String reserve(String saved, long now) {
        if (!allowed(saved, now)) throw new IllegalStateException("Recovery budget exhausted");
        List<String> result = new ArrayList<>();
        for (long at : recent(saved, now)) result.add(Long.toString(at));
        result.add(Long.toString(now));
        return String.join(",", result);
    }
}
