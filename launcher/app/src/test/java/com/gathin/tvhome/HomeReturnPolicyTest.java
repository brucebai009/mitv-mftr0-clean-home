package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeReturnPolicyTest {
    private final HomeReturnPolicy policy = new HomeReturnPolicy();
    private boolean home(boolean enabled, boolean interactive, boolean locked, long time) {
        return policy.shouldReturn(enabled, HomeReturnPolicy.STOCK_PACKAGE,
                HomeReturnPolicy.STOCK_HOME_CLASS, interactive, locked, time);
    }
    @Test public void realHomeWindowReturnsWhenArmed() { assertTrue(home(true, true, false, 0)); }
    @Test public void pauseAllowsStockHomeUntilReenabled() {
        assertFalse(home(false, true, false, 1000));
        assertTrue(home(true, true, false, 1001));
    }
    @Test public void neverTakesOverStockAppsPageOrUnknownWindow() {
        assertFalse(policy.shouldReturn(true, HomeReturnPolicy.STOCK_PACKAGE, "com.mitv.tvhome.NewMyAppActivity", true, false, 1000));
        assertFalse(policy.shouldReturn(true, HomeReturnPolicy.STOCK_PACKAGE, "android.widget.FrameLayout", true, false, 1000));
        assertFalse(policy.shouldReturn(true, HomeReturnPolicy.STOCK_PACKAGE, null, true, false, 1000));
    }
    @Test public void doesNotPullUserOutOfSettingsOtherAppsOrOwnDesktop() {
        for (String pkg : new String[]{"com.xiaomi.mitv.settings", "com.ts.launcher", "com.example.player", null}) {
            assertFalse(policy.shouldReturn(true, pkg, HomeReturnPolicy.STOCK_HOME_CLASS, true, false, 1000));
        }
    }
    @Test public void screenOffAndKeyguardAreNeverBypassed() {
        assertFalse(home(true, false, false, 1000));
        assertFalse(home(true, true, true, 1000));
        assertTrue(home(true, true, false, 1001));
    }
    @Test public void duplicateWindowEventsDoNotLoopButNextHomeWorks() {
        assertTrue(home(true, true, false, 1000));
        assertFalse(home(true, true, false, 1001));
        assertFalse(home(true, true, false, 1399));
        assertTrue(home(true, true, false, 1400));
        assertTrue(home(true, true, false, 5000));
    }
}
