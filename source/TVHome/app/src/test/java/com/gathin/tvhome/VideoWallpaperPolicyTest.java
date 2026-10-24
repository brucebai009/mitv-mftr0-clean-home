package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VideoWallpaperPolicyTest {
    @Test public void acceptsConstrainedSilentH264Video() {
        assertEquals(VideoWallpaperPolicy.Result.VALID,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 30, true, true));
    }

    @Test public void rejectsOversizedLongFastOrNonH264Video() {
        assertEquals(VideoWallpaperPolicy.Result.TOO_LARGE,
                VideoWallpaperPolicy.validate("loop.mp4", VideoWallpaperPolicy.MAX_BYTES + 1, 1920, 1080, 30_000L, 30, true, true));
        assertEquals(VideoWallpaperPolicy.Result.TOO_LONG,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 120_001L, 30, true, true));
        assertEquals(VideoWallpaperPolicy.Result.TOO_FAST,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 31, true, true));
        assertEquals(VideoWallpaperPolicy.Result.NOT_H264,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 30, false, true));
    }

    @Test public void rejectsNonMp4UnsafeNameAndNonSdr() {
        assertEquals(VideoWallpaperPolicy.Result.BAD_NAME,
                VideoWallpaperPolicy.validate("../loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 30, true, true));
        assertEquals(VideoWallpaperPolicy.Result.BAD_FORMAT,
                VideoWallpaperPolicy.validate("loop.mkv", 20_000_000L, 1920, 1080, 30_000L, 30, true, true));
        assertEquals(VideoWallpaperPolicy.Result.NOT_SDR,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 30, true, false));
    }

    @Test public void safeStorageNameIsStableAndDoesNotAcceptPaths() {
        assertTrue(VideoWallpaperPolicy.safeSourceName("fabric-loop.mp4"));
        assertFalse(VideoWallpaperPolicy.safeSourceName("C:\\temp\\fabric-loop.mp4"));
        assertFalse(VideoWallpaperPolicy.safeSourceName(""));
        assertEquals("video-wallpaper-abcdef0123456789.mp4",
                VideoWallpaperPolicy.storageName("fabric-loop.mp4", "abcdef0123456789deadbeef"));
    }
}
