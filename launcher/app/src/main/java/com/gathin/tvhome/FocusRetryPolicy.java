package com.gathin.tvhome;

/** Bounded retry window for a focus target that is laid out but not yet accepted by the window. */
final class FocusRetryPolicy {
    static final int MAX_ATTEMPTS = 12;

    private FocusRetryPolicy() { }

    static boolean shouldRetry(int attempts) {
        return attempts < MAX_ATTEMPTS;
    }
}
