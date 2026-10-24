package com.gathin.tvhome;

/** Monotonic frame-delivery watchdog. Pixel changes are not needed for a still scene. */
final class PlaybackHealth {
    enum Action { WAIT, RETRY, FALLBACK }
    private long began, lastFrame;
    private boolean sawFrame, retried, ended;
    void start(long now) { began = now; lastFrame = now; sawFrame = retried = ended = false; }
    void frame(long now) { lastFrame = now; sawFrame = true; }
    Action poll(long now) {
        if (ended) return Action.WAIT;
        long timeout = sawFrame ? 6_000L : 10_000L;
        if (now - (sawFrame ? lastFrame : began) < timeout) return Action.WAIT;
        if (retried) { ended = true; return Action.FALLBACK; }
        retried = true; began = lastFrame = now; sawFrame = false;
        return Action.RETRY;
    }
}
