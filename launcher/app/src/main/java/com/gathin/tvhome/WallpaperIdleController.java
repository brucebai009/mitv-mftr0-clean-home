package com.gathin.tvhome;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/** Fades the controls only. The wallpaper surface, decoder, bookmark and audio state are untouched. */
final class WallpaperIdleController {
    static volatile boolean lastIdle;
    static volatile int lastMinutes = 4;
    static volatile boolean lastScreenGuard;
    static volatile String lastEntryReason = "NOT_RUN";
    private final Activity activity;
    private final View root;
    private final ViewGroup controls;
    private final Runnable restored;
    private final WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable deadline = this::onDeadline;
    private View previousFocus;
    private boolean shownIdle, homeVisible, eligible, animations, reducedMotion, consumeTouch, closed;
    private int swallowedKey = KeyEvent.KEYCODE_UNKNOWN;
    private long swallowedDownTime;
    private boolean enteringKeyGesture;

    WallpaperIdleController(Activity activity, View root, ViewGroup controls, Runnable restored) {
        this.activity = activity; this.root = root; this.controls = controls; this.restored = restored;
        root.setFocusableInTouchMode(true);
        lastIdle = false;
    }

    boolean isIdle() { return shownIdle; }

    void configure(int minutes, boolean animations, boolean reducedMotion) {
        this.animations = animations; this.reducedMotion = reducedMotion;
        lastMinutes = WallpaperIdlePolicy.normalizeMinutes(minutes);
        timer.configure(lastMinutes, SystemClock.uptimeMillis());
        if (shownIdle && !timer.idle()) reveal(false);
        screenFlag();
        schedule();
    }

    void setEligible(boolean value) {
        if (closed) return;
        eligible = value;
        timer.eligible(value, SystemClock.uptimeMillis());
        if (!value && shownIdle) reveal(false);
        screenFlag();
        schedule();
    }

    void setHomeVisible(boolean value) {
        homeVisible = value;
        screenFlag();
    }

    private void screenFlag() {
        // Owned popups may pause the idle timer without surrendering the visible Home screen.
        lastScreenGuard = !closed && homeVisible && (timer.enabled() || shownIdle);
        if (lastScreenGuard) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void userActivity() {
        if (closed) return;
        timer.activity(SystemClock.uptimeMillis());
        if (shownIdle) reveal(true);
        schedule();
    }

    boolean handleKey(KeyEvent event) {
        if (closed) return false;
        int key = event.getKeyCode();
        if (swallowedKey == key && swallowedDownTime == event.getDownTime()) {
            // The Up press that ENTERS idle must not immediately wake it on repeat or release.
            if (!enteringKeyGesture) userActivity();
            if (event.getAction() == KeyEvent.ACTION_UP) {
                swallowedKey = KeyEvent.KEYCODE_UNKNOWN;
                enteringKeyGesture = false;
            }
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        boolean consume = shownIdle && WallpaperIdlePolicy.wakeOnlyKey(key);
        if (consume) { swallowedKey = key; swallowedDownTime = event.getDownTime(); enteringKeyGesture = false; }
        userActivity();
        return consume;
    }

    boolean handleTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            consumeTouch = shownIdle;
            userActivity();
        }
        boolean result = consumeTouch;
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
            consumeTouch = false;
        return result;
    }

    private void schedule() {
        handler.removeCallbacks(deadline);
        if (closed) return;
        long delay = timer.remaining(SystemClock.uptimeMillis());
        if (delay >= 0) handler.postDelayed(deadline, Math.max(1, delay));
    }

    private void onDeadline() {
        if (closed || !timer.enterIfDue(SystemClock.uptimeMillis())) { schedule(); return; }
        showIdle("TIMEOUT");
    }

    boolean enterFromTop(KeyEvent event) {
        if (closed || !homeVisible || !eligible || event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP
                || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                || !timer.enterNow()) return false;
        swallowedKey = event.getKeyCode(); swallowedDownTime = event.getDownTime();
        enteringKeyGesture = true;
        handler.removeCallbacks(deadline);
        showIdle("TOP_UP");
        return true;
    }

    private void showIdle(String reason) {
        previousFocus = activity.getCurrentFocus();
        shownIdle = true; lastIdle = true;
        lastEntryReason = reason;
        screenFlag();
        controls.animate().cancel();
        controls.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        controls.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        root.requestFocus();
        Log.i("TvHome.Idle", "ENTER reason=" + reason + " timeoutMinutes=" + lastMinutes);
        long duration = !animations ? 0 : reducedMotion ? 100 : 220;
        controls.animate().alpha(0f).translationY(reducedMotion ? 0 : 8 * controls.getResources().getDisplayMetrics().density)
                .setDuration(duration).withEndAction(() -> {
                    if (shownIdle && !closed) controls.setVisibility(View.INVISIBLE);
                }).start();
    }

    private void reveal(boolean animate) {
        shownIdle = false; lastIdle = false;
        screenFlag();
        controls.animate().cancel();
        controls.setVisibility(View.VISIBLE);
        controls.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        controls.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (eligible && previousFocus != null && previousFocus.isAttachedToWindow() && previousFocus.isShown())
            previousFocus.requestFocus();
        previousFocus = null;
        long duration = !animate || !animations ? 0 : reducedMotion ? 100 : 240;
        if (duration == 0) { controls.setAlpha(1f); controls.setTranslationY(0f); }
        else controls.animate().alpha(1f).translationY(0f).setDuration(duration)
                .withEndAction(() -> { if (!closed && !shownIdle && eligible) restored.run(); }).start();
        if (eligible) restored.run();
        Log.i("TvHome.Idle", "EXIT");
    }

    void close() {
        closed = true;
        handler.removeCallbacks(deadline);
        controls.animate().cancel();
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lastIdle = false;
        lastScreenGuard = false;
    }
}
