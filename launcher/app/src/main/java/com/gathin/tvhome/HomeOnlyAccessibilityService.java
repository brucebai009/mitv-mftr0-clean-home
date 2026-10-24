package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional, user-enabled fallback for firmware that will not make TVHome the default HOME.
 * It intentionally handles only KEYCODE_HOME.  It does not request window content, inspect windows,
 * inject gestures, draw overlays, or write secure settings.
 */
public final class HomeOnlyAccessibilityService extends AccessibilityService {
    static final ComponentName STOCK_HOME = new ComponentName("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");

    private final HomeGestureController gestures = new HomeGestureController();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        // This is only a TVHome-owned marker. The system accessibility setting remains the user's
        // authority and is checked again by HomeBootReceiver after every boot.
        RecoveryState.markHelperEnabled(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!RecoveryState.helperEnabled(this)) { gestures.reset(); return false; }
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (event.getAction() != KeyEvent.ACTION_UP) return true;
        HomeGestureController.Result result = gestures.onHome(
                event.getEventTime(), event.isCanceled(), event.isLongPress(), event.getRepeatCount() > 0);
        if (result == HomeGestureController.Result.NEW_HOME) {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(HomeIdentity.activity(this))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } else if (result == HomeGestureController.Result.STOCK_HOME) {
            startActivity(new Intent(Intent.ACTION_MAIN).setComponent(STOCK_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { gestures.reset(); }
}
