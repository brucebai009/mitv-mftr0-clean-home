package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static com.gathin.tvhome.WallpaperForegroundPolicy.Change.*;

public class WallpaperForegroundPolicyTest {
    @Test public void ownInputMenuDoesNotStopOrRestartVideo() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        assertEquals(START, policy.update(true, true, false, false));
        assertEquals(NONE, policy.update(true, false, true, false));
        assertEquals(NONE, policy.update(true, true, false, false));
    }
    @Test public void appLibraryIsASeparatePageEvenIfItIsAPopupWindow() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        policy.update(true, true, false, false);
        assertEquals(STOP, policy.update(true, false, true, true));
        assertEquals(NONE, policy.update(true, true, false, true));
        assertEquals(START, policy.update(true, true, false, false));
    }
    @Test public void leavingFromMenuStillReleasesDecoder() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        policy.update(true, true, false, false);
        assertEquals(STOP, policy.update(false, false, true, false));
    }
    @Test public void waitsForFirstFocusedFrame() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        assertEquals(NONE, policy.update(true, false));
        assertEquals(START, policy.update(true, true));
        assertEquals(NONE, policy.update(true, true));
    }
    @Test public void vendorSettingsOverlayCannotRestartVideoWhileActivityIsResumed() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        assertEquals(START, policy.update(true, true));
        assertEquals(STOP, policy.update(false, true));
        assertEquals(NONE, policy.update(false, false));
        assertEquals(NONE, policy.update(true, false));
        assertEquals(NONE, policy.update(true, false));
        assertEquals(START, policy.update(true, true));
    }
    @Test public void focusOnlyOverlayReleasesDecoderWithoutOnPause() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        assertEquals(START, policy.update(true, true));
        assertEquals(STOP, policy.update(true, false));
        assertEquals(START, policy.update(true, true));
    }
    @Test public void lateFocusCallbackAfterLeavingDoesNotResumePlayback() {
        WallpaperForegroundPolicy policy = new WallpaperForegroundPolicy();
        assertEquals(START, policy.update(true, true));
        assertEquals(STOP, policy.update(false, false));
        assertEquals(NONE, policy.update(false, true));
        assertEquals(START, policy.update(true, true));
    }
}
