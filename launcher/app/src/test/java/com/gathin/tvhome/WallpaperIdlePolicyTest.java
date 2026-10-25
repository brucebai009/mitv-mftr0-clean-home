package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperIdlePolicyTest {
    @Test public void fourMinuteDeadlineAndFirstWakeAreExact() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.eligible(true, 100);
        assertFalse(timer.enterIfDue(240099));
        assertTrue(timer.enterIfDue(240100));
        assertTrue(timer.idle());
        assertFalse(timer.enterIfDue(240101));
        timer.activity(241000);
        assertFalse(timer.idle());
        assertEquals(240000, timer.remaining(241000));
    }
    @Test public void externalPageAndPopupTimeDoesNotAccumulate() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.eligible(true, 0);
        timer.eligible(false, 239000);
        assertFalse(timer.enterIfDue(999999));
        timer.eligible(true, 1000000);
        assertEquals(240000, timer.remaining(1000000));
        assertFalse(timer.enterIfDue(1000001));
    }
    @Test public void repeatedLifecycleSyncDoesNotPostponeInactivity() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.eligible(true, 0);
        timer.configure(4, 100000);
        timer.eligible(true, 200000);
        assertTrue(timer.enterIfDue(240000));
    }
    @Test public void activityRestartsAndOffDisablesTheTimer() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.configure(1, 0); timer.eligible(true, 0);
        timer.activity(59000);
        assertFalse(timer.enterIfDue(60000));
        assertTrue(timer.enterIfDue(119000));
        timer.configure(0, 120000);
        assertFalse(timer.idle());
        assertFalse(timer.enterIfDue(Long.MAX_VALUE));
        assertEquals(-1, timer.remaining(120001));
    }
    @Test public void invalidPersistedValuesUseTheOwnerDefault() {
        for (int value : new int[]{-1, 3, 100000, Integer.MAX_VALUE}) assertEquals(4, WallpaperIdlePolicy.normalizeMinutes(value));
        for (int value : new int[]{0, 1, 2, 4, 5, 10, 15, 30}) assertEquals(value, WallpaperIdlePolicy.normalizeMinutes(value));
    }
    @Test public void wakeConsumesNavigationButPreservesSystemKeys() {
        for (int key : new int[]{4, 19, 20, 21, 22, 23, 66, 82, 160}) assertTrue(WallpaperIdlePolicy.wakeOnlyKey(key));
        for (int key : new int[]{3, 24, 25, 26, 84, 164, 219, 223, 224}) assertFalse(WallpaperIdlePolicy.wakeOnlyKey(key));
    }
    @Test public void manualEntryWorksWithAutomaticTimeoutOffAndWakeRestoresOff() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.configure(0, 0);
        assertFalse(timer.enterNow());
        timer.eligible(true, 10);
        assertTrue(timer.enterNow());
        assertTrue(timer.idle());
        assertFalse(timer.enterNow());
        assertEquals(-1, timer.remaining(100));
        timer.activity(200);
        assertFalse(timer.idle());
        assertFalse(timer.enabled());
        assertEquals(-1, timer.remaining(1000000));
    }
    @Test public void leavingHomeCancelsManualIdleAndResetsAutomaticDeadline() {
        WallpaperIdlePolicy timer = new WallpaperIdlePolicy();
        timer.eligible(true, 0);
        assertTrue(timer.enterNow());
        timer.eligible(false, 100);
        assertFalse(timer.idle());
        assertFalse(timer.enterNow());
        timer.eligible(true, 200);
        assertEquals(240000, timer.remaining(200));
    }
}
