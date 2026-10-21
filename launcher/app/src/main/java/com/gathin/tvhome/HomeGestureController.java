package com.gathin.tvhome;

/**
 * Pure state machine for the optional physical-Home helper.  It deliberately has no Android or
 * accessibility dependency so the timing contract can be tested on the JVM and cannot accidentally
 * grow window-reading, gesture-injection, or secure-settings behavior.
 */
final class HomeGestureController {
    static final long MAX_TRIPLE_WINDOW_MS = 1_200L;
    static final long MAX_ADJACENT_GAP_MS = 600L;

    enum Result { NONE, NEW_HOME, STOCK_HOME }

    private long firstAt = Long.MIN_VALUE;
    private long lastAt = Long.MIN_VALUE;
    private int count;

    Result onHome(long uptimeMs) {
        return onHome(uptimeMs, false, false, false);
    }

    Result onHome(long uptimeMs, boolean canceled, boolean longPress, boolean repeat) {
        if (canceled || longPress || repeat) return Result.NONE;
        // A helper may receive the same physical press through both its key and Intent paths.
        if (uptimeMs == lastAt) return Result.NONE;
        if (count == 0 || uptimeMs < lastAt
                || uptimeMs - firstAt > MAX_TRIPLE_WINDOW_MS
                || uptimeMs - lastAt > MAX_ADJACENT_GAP_MS) {
            firstAt = uptimeMs;
            lastAt = uptimeMs;
            count = 1;
            return Result.NEW_HOME;
        }

        lastAt = uptimeMs;
        count++;
        if (count >= 3) {
            reset();
            return Result.STOCK_HOME;
        }
        return Result.NEW_HOME;
    }

    void reset() {
        firstAt = Long.MIN_VALUE;
        lastAt = Long.MIN_VALUE;
        count = 0;
    }
}
