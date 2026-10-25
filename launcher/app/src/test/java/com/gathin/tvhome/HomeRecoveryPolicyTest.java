package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class HomeRecoveryPolicyTest {
    private static final String OWN = "com.ts.launcher/com.gathin.tvhome.HomeReturnService";
    @Test public void preservesVoiceSpellingAndOrder() {
        assertEquals("voice/.Service:other/other.Service:" + OWN, new HomeServiceList("voice/.Service:other/other.Service").with(OWN));
    }
    @Test public void removesOnlyOwnIncludingDuplicateForms() {
        assertEquals("voice/.Service", new HomeServiceList("app/.Service:voice/.Service:app/app.Service").without("app/app.Service"));
    }
    @Test public void reReadPreservesConcurrentAddition() {
        String afterRemoval = new HomeServiceList("voice/.Service:" + OWN).without(OWN);
        assertEquals("voice/.Service:new/.Service:" + OWN, new HomeServiceList(afterRemoval + ":new/.Service").with(OWN));
    }
    @Test public void emptyListAndNullAreSupported() {
        assertEquals(OWN, new HomeServiceList(null).with(OWN));
        assertEquals(OWN, new HomeServiceList("null").with(OWN));
        assertEquals("", new HomeServiceList(OWN).without(OWN));
    }
    @Test(expected = IllegalArgumentException.class) public void malformedListRefusesWrite() { new HomeServiceList("voice/.Service:broken"); }
    @Test(expected = IllegalArgumentException.class) public void emptyEntryRefusesWrite() { new HomeServiceList("voice/.Service:"); }
    @Test public void noRapidRetry() { assertFalse(HomeRecoveryBudget.allowed("10000", 14999)); assertTrue(HomeRecoveryBudget.allowed("10000", 15000)); }
    @Test public void budgetPersistsAcrossInstancesAndExpires() {
        String saved = HomeRecoveryBudget.reserve("", 10000);
        saved = HomeRecoveryBudget.reserve(saved, 15000);
        saved = HomeRecoveryBudget.reserve(saved, 20000);
        assertFalse(HomeRecoveryBudget.allowed(saved, 60000));
        assertTrue(HomeRecoveryBudget.allowed(saved, 70000));
    }
    @Test public void clockRollbackDoesNotRefillBudget() { assertFalse(HomeRecoveryBudget.allowed("20000", 10000)); }
    @Test public void rootProbeOnlyAcceptsStockWhileAwakeAndEnabled() {
        assertTrue(HomeReturnPolicy.canRestoreFromRoot(true, true, false, "com.mitv.tvhome"));
        for (String pkg : new String[]{"", "android", "com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.settings", "com.ts.launcher", "com.xiaomi.mitv.hyper.screensaver"}) {
            assertFalse(HomeReturnPolicy.canRestoreFromRoot(true, true, false, pkg));
        }
        assertFalse(HomeReturnPolicy.canRestoreFromRoot(false, true, false, "com.mitv.tvhome"));
        assertFalse(HomeReturnPolicy.canRestoreFromRoot(true, false, false, "com.mitv.tvhome"));
        assertFalse(HomeReturnPolicy.canRestoreFromRoot(true, true, true, "com.mitv.tvhome"));
    }
}
