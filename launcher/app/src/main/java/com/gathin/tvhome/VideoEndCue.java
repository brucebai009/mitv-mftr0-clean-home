package com.gathin.tvhome;

import android.os.Handler;
import java.util.function.LongSupplier;

/** Signals one visual fade near the tail. Never completes, seeks or advances playback. */
final class VideoEndCue {
    static final long LEAD_MS = 650;
    private final Handler worker;
    private Runnable pending;

    VideoEndCue(Handler worker) { this.worker = worker; }

    void start(LongSupplier remaining, Runnable fading) {
        cancel();
        pending = new Runnable() {
            @Override public void run() {
                if (pending != this) return;
                final long left;
                try { left = remaining.getAsLong(); }
                catch (RuntimeException unavailable) { pending = null; return; }
                if (left <= LEAD_MS) { pending = null; fading.run(); }
                else worker.postDelayed(this, Math.max(50, left - LEAD_MS));
            }
        };
        worker.post(pending);
    }

    void cancel() {
        if (pending != null) worker.removeCallbacks(pending);
        pending = null;
    }
}
