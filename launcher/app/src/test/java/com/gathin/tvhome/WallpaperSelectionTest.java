package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallpaperSelectionTest {
    @Test public void sameSelectionDoesNotReallocateDecoderOnEveryFocusEvent() {
        WallpaperSelection a = new WallpaperSelection("video", "rain", "/videos/rain.mp4", false, 0);
        WallpaperSelection b = new WallpaperSelection("video", "rain", "/videos/rain.mp4", false, 0);
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode());
    }
    @Test public void everyEffectiveChangeInvalidatesTheOldPlayer() {
        WallpaperSelection a = new WallpaperSelection("video", "rain", "/videos/rain.mp4", false, 0);
        assertNotEquals(a, new WallpaperSelection("image", "rain", "/videos/rain.mp4", false, 0));
        assertNotEquals(a, new WallpaperSelection("video", "train", "/videos/rain.mp4", false, 0));
        assertNotEquals(a, new WallpaperSelection("video", "rain", "/videos/train.mp4", false, 0));
        assertNotEquals(a, new WallpaperSelection("video", "rain", "/videos/rain.mp4", true, 0));
        assertNotEquals(a, new WallpaperSelection("video", "rain", "/videos/rain.mp4", false, 20));
        assertNotEquals(a, null);
    }
}
