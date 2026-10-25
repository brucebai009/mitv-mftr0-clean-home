package com.gathin.tvhome;

import android.app.Activity;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class WallpaperIdleControllerTest {
    @Test public void topUpEntersReleaseAndRepeatStayIdleNextConfirmOnlyWakes() {
        try (ActivityController<Activity> lifecycle = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = lifecycle.get();
            FrameLayout root = new FrameLayout(activity), controls = new FrameLayout(activity);
            TextView button = new TextView(activity); button.setFocusable(true); button.setFocusableInTouchMode(true);
            controls.addView(button); root.addView(controls); activity.setContentView(root);
            button.requestFocus();
            WallpaperIdleController idle = new WallpaperIdleController(activity, root, controls, () -> {});
            idle.configure(0, false, false); idle.setHomeVisible(true); idle.setEligible(true);
            long time = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0);
            assertTrue(idle.enterFromTop(down));
            assertTrue(idle.isIdle());
            assertEquals("TOP_UP", WallpaperIdleController.lastEntryReason);
            assertTrue((activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
            assertTrue(idle.handleKey(new KeyEvent(time, time + 10, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1)));
            assertTrue(idle.isIdle());
            assertTrue(idle.handleKey(new KeyEvent(time, time + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP, 0)));
            assertTrue(idle.isIdle());
            assertTrue(idle.handleKey(new KeyEvent(time + 30, time + 30, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0)));
            assertFalse(idle.isIdle());
            assertTrue(idle.handleKey(new KeyEvent(time + 30, time + 40, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0)));
            assertEquals(View.VISIBLE, controls.getVisibility());
            assertEquals(0f, controls.getTranslationY(), 0f);
            assertFalse(WallpaperIdleController.lastScreenGuard);
            idle.close();
        }
    }
    @Test public void heldUpAndOtherWindowsCannotEnterManualIdle() {
        try (ActivityController<Activity> lifecycle = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = lifecycle.get(); FrameLayout root = new FrameLayout(activity);
            FrameLayout controls = new FrameLayout(activity); root.addView(controls); activity.setContentView(root);
            WallpaperIdleController idle = new WallpaperIdleController(activity, root, controls, () -> {});
            idle.configure(4, false, false); idle.setHomeVisible(true); idle.setEligible(true);
            assertFalse(idle.enterFromTop(new KeyEvent(1, 2, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 1)));
            idle.setEligible(false);
            assertFalse(idle.enterFromTop(new KeyEvent(3, 3, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0)));
            idle.setEligible(true); idle.setHomeVisible(false);
            assertFalse(idle.enterFromTop(new KeyEvent(4, 4, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0)));
            assertFalse(idle.isIdle()); idle.close();
        }
    }
}
