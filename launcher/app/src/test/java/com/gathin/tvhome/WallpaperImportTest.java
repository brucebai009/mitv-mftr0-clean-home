package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallpaperImportTest {
    @Test public void acceptsTheThreeSupportedStaticFormats() {
        assertTrue(WallpaperImport.supportedExtension("photo.JPG"));
        assertTrue(WallpaperImport.supportedExtension("photo.png"));
        assertTrue(WallpaperImport.supportedExtension("photo.webp"));
        assertFalse(WallpaperImport.supportedExtension("photo.gif"));
        assertFalse(WallpaperImport.supportedExtension("photo"));
    }

    @Test public void dimsAreClampedToTenPercentSteps() {
        assertEquals(30, WallpaperImport.normalizeDim(30));
        assertEquals(0, WallpaperImport.normalizeDim(-1));
        assertEquals(70, WallpaperImport.normalizeDim(99));
        assertEquals(30, WallpaperImport.normalizeDim(34));
        assertEquals(40, WallpaperImport.normalizeDim(35));
    }

    @Test public void generatedNamesAreSafeAndDoNotContainTheSourceName() {
        String name = WallpaperImport.fileName("holiday picture../JPG", "0123456789abcdef");
        assertEquals("wallpaper-0123456789abcdef.jpg", name);
        assertFalse(name.contains(".."));
        assertFalse(name.contains("/"));
        assertFalse(name.contains("\\"));
    }
}
