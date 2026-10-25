package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Event driven. Root access is limited to package ownership; no text or node traversal. */
public final class HomeReturnService extends AccessibilityService {
    private static final String TAG = "TvHome.HomeReturn";
    private static volatile HomeReturnService connected;
    static volatile int attempts;
    static volatile int windowEvents;
    static volatile String lastWindowPackage = "", lastWindowClass = "";
    static volatile String lastResult = "NOT_ATTEMPTED";
    private final HomeReturnPolicy policy = new HomeReturnPolicy();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    @Override protected void onServiceConnected() {
        connected = this;
        HomeReturnDiagnostics.record(this, "SERVICE_CONNECTED", "enabled=" + HomeReturnState.enabled(this));
        // A rebind can happen after PatchWall's window event was missed.
        schedule("SERVICE_CONNECTED", 1_500);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        windowEvents++;
        lastWindowPackage = pkg;
        lastWindowClass = cls;
        if (!HomeReturnPolicy.STOCK_PACKAGE.equals(pkg)) {
            // System dialogs, HDMI and other apps cancel a pending takeover.
            generation++;
            return;
        }
        boolean eligible = policy.shouldReturn(HomeReturnState.enabled(this), pkg, cls,
                interactive(), locked(), SystemClock.uptimeMillis());
        Log.i(TAG, "window package=" + pkg + " class=" + cls + " eligible=" + eligible);
        if (eligible) schedule("STOCK_HOME_EVENT", 120);
        else if (!HomeReturnPolicy.STOCK_HOME_CLASS.equals(cls)) generation++;
    }
    private boolean interactive() {
        PowerManager power = getSystemService(PowerManager.class);
        return power != null && power.isInteractive();
    }
    private boolean locked() {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isKeyguardLocked();
    }
    static void checkStockLater(String reason) {
        HomeReturnService service = connected;
        if (service != null) service.schedule(reason, 1_500);
    }
    private void schedule(String reason, long delay) {
        int ticket = ++generation;
        main.postDelayed(() -> probe(ticket, reason, 0), delay);
    }
    private void probe(int ticket, String reason, int index) {
        if (ticket != generation || connected != this) return;
        if (!HomeReturnState.enabled(this) || !interactive() || locked()) return;
        AccessibilityNodeInfo root = null;
        String activePackage = "";
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) activePackage = root.getPackageName().toString();
        } catch (RuntimeException error) {
            HomeReturnDiagnostics.record(this, "ROOT_CHECK_REJECTED", error.getClass().getSimpleName());
            return;
        } finally { if (root != null) root.recycle(); }
        if (activePackage.isEmpty()) {
            // At most three checks per connection/window/wake event, never a permanent poll.
            if (index < 2) main.postDelayed(() -> probe(ticket, reason, index + 1), 1_500);
            else HomeReturnDiagnostics.record(this, "ROOT_UNKNOWN_NO_TAKEOVER", reason);
            return;
        }
        if (!HomeReturnPolicy.canRestoreFromRoot(HomeReturnState.enabled(this), interactive(), locked(), activePackage)) {
            HomeReturnDiagnostics.record(this, "KEEP_EXTERNAL_FOREGROUND", reason + ":" + activePackage);
            return;
        }
        attempts++;
        HomeReturnDiagnostics.record(this, "LAUNCH_REQUEST", reason);
        try {
            startActivity(HomeReturnState.launcherIntent(this));
            lastResult = "LAUNCH_REQUESTED";
        } catch (RuntimeException denied) {
            lastResult = "REJECTED_" + denied.getClass().getSimpleName();
            HomeReturnDiagnostics.record(this, "LAUNCH_REJECTED", reason + ":" + denied.getClass().getSimpleName());
        }
    }
    static boolean isConnected() { return connected != null; }
    static boolean openStockAfterPause() {
        HomeReturnService service = connected;
        return service != null && service.performGlobalAction(GLOBAL_ACTION_HOME);
    }
    @Override public void onInterrupt() { HomeReturnDiagnostics.record(this, "SERVICE_INTERRUPTED", "system callback"); }
    @Override public boolean onUnbind(Intent intent) {
        generation++;
        if (connected == this) connected = null;
        HomeReturnDiagnostics.record(this, "SERVICE_UNBOUND", "system callback");
        return super.onUnbind(intent);
    }
    @Override public void onDestroy() {
        generation++;
        main.removeCallbacksAndMessages(null);
        if (connected == this) connected = null;
        HomeReturnDiagnostics.record(this, "SERVICE_DESTROYED", "system callback");
        super.onDestroy();
    }
}
