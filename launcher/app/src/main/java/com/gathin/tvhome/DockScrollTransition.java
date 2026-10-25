package com.gathin.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.BooleanSupplier;

/** One scroll/spring clock; focus arrives with the target row instead of after a settle timeout. */
final class DockScrollTransition {
    static volatile String lastResult = "NOT_RUN";
    static volatile long lastDurationMs, lastFocusDelayMs = -1, lastFocusAtMs = -1, lastAnimationEndMs = -1;
    static volatile boolean lastAnchorClamped;
    private final RecyclerView grid;
    private final View root;
    private Runnable watchdog, landingRequest, focusRetry;
    private ValueAnimator landingAnimator;
    private int oldFocusability, generation;
    private long started;
    private boolean running, focusDelivered;
    private BooleanSupplier focusTarget;
    private Runnable recoverFocus;

    DockScrollTransition(RecyclerView grid, View root) { this.grid = grid; this.root = root; }
    boolean running() { return running; }
    boolean targetHasFocus() { return focusDelivered; }

    static boolean consumeKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BACK;
    }

    void start(int position, int desiredTop, BooleanSupplier focusTarget, Runnable recoverFocus) {
        cancel();
        GridLayoutManager lm = (GridLayoutManager) grid.getLayoutManager();
        if (lm == null) { recoverFocus.run(); return; }
        this.focusTarget = focusTarget; this.recoverFocus = recoverFocus;
        running = true; focusDelivered = false;
        final int token = generation;
        started = SystemClock.uptimeMillis();
        lastResult = "RUNNING"; lastDurationMs = 0;
        lastFocusDelayMs = -1; lastFocusAtMs = -1; lastAnimationEndMs = -1; lastAnchorClamped = false;
        oldFocusability = grid.getDescendantFocusability();
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.requestFocus();
        // Only a detached/missing target can reach this watchdog. Normal completion is event-driven.
        watchdog = () -> { if (running && generation == token) recover("TARGET_TIMEOUT"); };
        grid.postDelayed(watchdog, 1200);
        if (grid.findViewHolderForAdapterPosition(position) != null) {
            queueLanding(lm, position, desiredTop, token);
        } else {
            LinearSmoothScroller seeker = new LinearSmoothScroller(grid.getContext()) {
                @Override protected void onTargetFound(View target, RecyclerView.State state,
                                                        RecyclerView.SmoothScroller.Action action) {
                    queueLanding(lm, position, desiredTop, token);
                }
            };
            seeker.setTargetPosition(position); lm.startSmoothScroll(seeker);
        }
    }

    private void queueLanding(GridLayoutManager lm, int position, int desiredTop, int token) {
        if (!running || generation != token || landingRequest != null || landingAnimator != null) return;
        landingRequest = () -> {
            landingRequest = null;
            if (!running || generation != token) return;
            grid.stopScroll();
            RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
            if (holder == null) return;
            int distance = lm.getDecoratedTop(holder.itemView) - desiredTop;
            if (Math.abs(distance) <= 1) { complete(token); return; }
            DockSpringCurve curve = new DockSpringCurve(distance, grid.getResources().getDisplayMetrics().density);
            final int[] applied = {0};
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            landingAnimator = animator;
            animator.setDuration(DockSpringCurve.DURATION_MS);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(value -> {
                if (!running || generation != token) return;
                float time = (float) value.getAnimatedValue();
                int scroll = curve.scrollPixels(time);
                grid.scrollBy(0, scroll - applied[0]); applied[0] = scroll;
                grid.setTranslationY(curve.translationPixels(time));
                // The real row is now at its destination; highlight follows the remaining tiny rebound.
                if (curve.progress(time) >= 1f) handOffFocus();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    if (!running || generation != token || landingAnimator != animator) return;
                    landingAnimator = null;
                    grid.setTranslationY(0f);
                    RecyclerView.ViewHolder target = grid.findViewHolderForAdapterPosition(position);
                    lastAnchorClamped = target != null
                            && Math.abs(lm.getDecoratedTop(target.itemView) - desiredTop) > 1;
                    // RecyclerView may legally clamp at a content edge. Do not wait 1.2 s for an impossible pixel.
                    complete(token);
                }
            });
            animator.start();
        };
        grid.post(landingRequest);
    }

    private boolean handOffFocus() {
        if (focusDelivered) return true;
        if (!running || grid.isComputingLayout() || grid.hasPendingAdapterUpdates()) return false;
        grid.setDescendantFocusability(oldFocusability);
        if (!focusTarget.getAsBoolean()) {
            grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            return false;
        }
        focusDelivered = true;
        lastFocusAtMs = SystemClock.uptimeMillis() - started;
        lastFocusDelayMs = lastAnimationEndMs < 0 ? 0 : Math.max(0, lastFocusAtMs - lastAnimationEndMs);
        return true;
    }

    private void complete(int token) {
        lastAnimationEndMs = SystemClock.uptimeMillis() - started;
        if (handOffFocus()) { finish(); return; }
        // A layout posted in this frame may still attach the holder. Allow two frames, not a fixed idle wait.
        focusRetry = new Runnable() {
            private int frames;
            @Override public void run() {
                if (!running || generation != token || focusRetry != this) return;
                if (handOffFocus()) { finish(); return; }
                if (++frames < 2) grid.postOnAnimation(this);
                else recover("FOCUS_TARGET_UNAVAILABLE");
            }
        };
        grid.postOnAnimation(focusRetry);
    }

    private void finish() {
        lastDurationMs = SystemClock.uptimeMillis() - started;
        lastResult = "SETTLED";
        cancel();
    }

    private void recover(String reason) {
        Runnable callback = recoverFocus;
        lastDurationMs = SystemClock.uptimeMillis() - started;
        lastResult = reason;
        cancel();
        if (callback != null) callback.run();
    }

    void cancel() {
        generation++;
        if (watchdog != null) grid.removeCallbacks(watchdog);
        if (landingRequest != null) grid.removeCallbacks(landingRequest);
        if (focusRetry != null) grid.removeCallbacks(focusRetry);
        watchdog = null; landingRequest = null; focusRetry = null;
        boolean restore = running;
        running = false; focusDelivered = false;
        focusTarget = null; recoverFocus = null;
        ValueAnimator old = landingAnimator; landingAnimator = null;
        if (old != null) old.cancel();
        grid.setTranslationY(0f);
        if (!restore) return;
        grid.stopScroll();
        grid.setDescendantFocusability(oldFocusability);
        if ("RUNNING".equals(lastResult)) lastResult = "CANCELLED";
    }
}
