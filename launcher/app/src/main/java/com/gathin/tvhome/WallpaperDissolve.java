package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** Fade through black on one video plane. Historical class name retained for a small upgrade diff. */
final class WallpaperDissolve {
    static final long FADE_OUT_MS = 500, FADE_IN_MS = 420, REDUCED_FADE_MS = 120;
    static volatile String lastStage = "NOT_RUN";
    static volatile long lastHoldMs, lastFadeMs;
    private final View overlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final long outMs, inMs;
    private WallpaperVideoSession current, outgoing;
    private String currentId = "";
    private ValueAnimator animator;
    private Runnable pendingReady, presentationTimeout;
    private int animationToken, presentationToken;
    private long cueAt, blackAt, eosAt, trace;
    private boolean waitingNext, paused, closed, releaseQueued, revealQueued;

    WallpaperDissolve(View overlay, boolean animations, boolean reducedMotion) {
        this.overlay = overlay;
        outMs = !animations ? 0 : reducedMotion ? REDUCED_FADE_MS : FADE_OUT_MS;
        inMs = !animations ? 0 : reducedMotion ? REDUCED_FADE_MS : FADE_IN_MS;
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setAlpha(0f); overlay.setVisibility(View.GONE);
    }

    boolean holding() { return waitingNext || overlay.getVisibility() == View.VISIBLE; }

    void bind(WallpaperVideoSession source, String id) { current = source; currentId = id; }

    void fadeOut(WallpaperVideoSession source) {
        if (closed || paused || source == null || source != current || waitingNext || holding()) return;
        cueAt = now(); blackAt = 0;
        overlay.setVisibility(View.VISIBLE); overlay.setAlpha(0f);
        lastStage = "FADING_OUT";
        animate(1f, duration(outMs), this::blackReady);
    }

    void hold(WallpaperVideoSession source, long count, String nextId, String reason, long completedAt,
              Runnable readyToRelease) {
        if (closed) { readyToRelease.run(); return; }
        // A decoder-release continuation is owned exactly once, including rapid errors/close.
        finishPending();
        if (trace != 0) TransitionJournal.event(trace, "interruptedAt", now());
        outgoing = source; waitingNext = true; releaseQueued = false; revealQueued = false;
        eosAt = completedAt; lastHoldMs = 0; lastFadeMs = 0;
        trace = TransitionJournal.begin(count, currentId, nextId, reason, completedAt,
                cueAt, blackAt, WallpaperIdleController.lastIdle);
        pendingReady = readyToRelease;
        overlay.setVisibility(View.VISIBLE);
        if (paused) { cancelAnimation(); overlay.setAlpha(1f); blackReady(); }
        else if (overlay.getAlpha() >= .999f) blackReady();
        else if (animator == null) {
            // Missing/late tail cue: fade the last submitted frame rather than abruptly cut to black.
            if (cueAt == 0) cueAt = now();
            TransitionJournal.event(trace, "fadeOutAt", cueAt);
            lastStage = "FADING_OUT";
            animate(1f, duration(outMs) * (1f - overlay.getAlpha()), this::blackReady);
        }
    }

    private void blackReady() {
        overlay.setAlpha(1f);
        if (blackAt == 0) blackAt = now();
        lastStage = "BLACK";
        if (trace != 0) TransitionJournal.event(trace, "blackAt", blackAt);
        if (!waitingNext || pendingReady == null || releaseQueued) return;
        releaseQueued = true;
        // Submit the opaque layer before tearing down the old Surface. Bounded even when detached.
        final int token = ++presentationToken;
        presentationTimeout = () -> { if (token == presentationToken) finishPending(); };
        main.postDelayed(presentationTimeout, 64);
        overlay.postOnAnimation(() -> overlay.postOnAnimation(() -> {
            if (token == presentationToken) finishPending();
        }));
    }

    private void finishPending() {
        ++presentationToken;
        if (presentationTimeout != null) main.removeCallbacks(presentationTimeout);
        presentationTimeout = null;
        Runnable next = pendingReady; pendingReady = null;
        if (next != null) {
            if (trace != 0) TransitionJournal.event(trace, "releaseAt", now());
            next.run();
        }
    }

    void firstFrame(WallpaperVideoSession source) {
        if (closed || paused || source != current || source == outgoing || !waitingNext || revealQueued) return;
        revealQueued = true;
        TransitionJournal.event(trace, "firstFrameAt", now());
        final int token = ++animationToken;
        overlay.postOnAnimation(() -> overlay.postOnAnimation(() -> {
            if (closed || paused || token != animationToken || source != current || !waitingNext) return;
            lastHoldMs = Math.max(0, now() - blackAt);
            lastStage = "FADING_IN";
            TransitionJournal.event(trace, "fadeInAt", now());
            final long began = now();
            animate(0f, duration(inMs), () -> {
                lastFadeMs = now() - began;
                TransitionJournal.event(trace, "settledAt", now());
                trace = 0; waitingNext = false; outgoing = null;
                cueAt = 0; blackAt = 0; revealQueued = false;
                overlay.setVisibility(View.GONE); overlay.setAlpha(0f);
                lastStage = "SETTLED";
            });
        }));
    }

    private void animate(float target, float milliseconds, Runnable done) {
        cancelAnimation();
        final int token = animationToken;
        if (milliseconds <= 0) { overlay.setAlpha(target); done.run(); return; }
        ValueAnimator value = ValueAnimator.ofFloat(overlay.getAlpha(), target); animator = value;
        value.setDuration(Math.round(milliseconds)); value.setInterpolator(new AccelerateDecelerateInterpolator());
        value.addUpdateListener(v -> {
            if (token == animationToken && !closed) overlay.setAlpha((float) v.getAnimatedValue());
        });
        value.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (animator != value || token != animationToken || closed || paused) return;
                animator = null; done.run();
            }
        });
        value.start();
    }

    void pause() {
        paused = true; cancelAnimation(); revealQueued = false;
        if (waitingNext) {
            TransitionJournal.event(trace, "pausedAt", now());
            overlay.setAlpha(1f); overlay.setVisibility(View.VISIBLE); blackReady();
        } else {
            // Before EOS it is still the same video. Resume its saved position, then re-arm the cue.
            cueAt = blackAt = 0; overlay.setAlpha(0f); overlay.setVisibility(View.GONE);
        }
    }
    void resume() { paused = false; }

    void abort() {
        cancelAnimation();
        if (trace != 0) TransitionJournal.event(trace, "failedAt", now());
        finishPending(); trace = 0; waitingNext = false; revealQueued = false;
        outgoing = null; cueAt = blackAt = 0;
        overlay.setAlpha(0f); overlay.setVisibility(View.GONE); lastStage = "ERROR_POSTER";
    }

    void close() {
        if (closed) return;
        closed = true; cancelAnimation();
        if (trace != 0) TransitionJournal.event(trace, "interruptedAt", now());
        finishPending(); trace = 0; current = outgoing = null; waitingNext = false;
        overlay.setAlpha(0f); overlay.setVisibility(View.GONE); lastStage = "CLOSED";
    }

    private void cancelAnimation() {
        ++animationToken;
        ValueAnimator old = animator; animator = null;
        if (old != null) old.cancel();
    }
    private static long duration(long requested) { return ValueAnimator.areAnimatorsEnabled() ? requested : 0; }
    private static long now() { return SystemClock.elapsedRealtime(); }
}
