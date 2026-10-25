package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IconCardArtworkTest {
    @Test public void onlyNonBannerArtworkUsesTheFallback() {
        assertFalse(IconCardArtwork.needsFallback(1920, 1080));
        assertTrue(IconCardArtwork.needsFallback(64, 64));
        assertTrue(IconCardArtwork.needsFallback(0, 0));
    }

    @Test public void averageColorIgnoresTransparentPixelsAndLiftsDarkIcons() {
        int color = IconCardArtwork.averageOpaqueColor(new int[] {
                0x00000000, 0xFF202020, 0xFFFF0000
        });
        assertEquals(0xFFFF0000, color);
        assertEquals(0xFF444444, IconCardArtwork.averageOpaqueColor(new int[] {0x00000000}));
    }

    @Test public void cacheKeyIncludesComponentVersionSizeAndStyle() {
        String a = IconCardArtwork.cacheKey("com.example/.Main", 10L, 192, 108, 1);
        String b = IconCardArtwork.cacheKey("com.example/.Main", 11L, 192, 108, 1);
        String c = IconCardArtwork.cacheKey("com.example/.Main", 10L, 180, 112, 1);
        assertNotNull(a);
        assertFalse(a.equals(b));
        assertFalse(a.equals(c));
    }

    @Test public void cacheIsByteBoundedAndCanInvalidateByPrefix() {
        IconCardArtwork.BitmapCache cache = new IconCardArtwork.BitmapCache(128);
        cache.putBytesForTest("com.a/.Main|1|192|108|1", 64);
        cache.putBytesForTest("com.b/.Main|1|192|108|1", 64);
        assertTrue(cache.bytes() <= 128);
        assertTrue(cache.containsKeyForTest("com.b/.Main|1|192|108|1"));
        cache.invalidatePrefix("com.b/.Main");
        assertFalse(cache.containsKeyForTest("com.b/.Main|1|192|108|1"));
    }
}
