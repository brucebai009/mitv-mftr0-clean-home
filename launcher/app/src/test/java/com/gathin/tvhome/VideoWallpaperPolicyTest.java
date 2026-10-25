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
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, VideoWallpaperPolicy.MAX_DURATION_MS + 1, 30, true, true));
        assertEquals(VideoWallpaperPolicy.Result.TOO_FAST,
                VideoWallpaperPolicy.validate("loop.mp4", 20_000_000L, 1920, 1080, 30_000L, 61, true, true));
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


    @Test public void acceptsOriginalLargeUhdHevc60WithoutTranscoding() {
        assertEquals(VideoWallpaperPolicy.Result.VALID, VideoWallpaperPolicy.validate(
                "favorite.mp4", 680L * 1024 * 1024, 3840, 2160, 600_000L, 60, "video/hevc", true));
        assertEquals(VideoWallpaperPolicy.Result.VALID, VideoWallpaperPolicy.validate(
                "space.mp4", 60_000_000L, 3840, 2160, 30_000L, 30, "video/avc", true));
    }
    @Test public void rejectsUnsupportedOriginalsInsteadOfSilentlyReducingQuality() {
        assertEquals(VideoWallpaperPolicy.Result.TOO_WIDE, VideoWallpaperPolicy.validate(
                "x.mp4", 1_000_000L, 7680, 2160, 1000, 60, "video/hevc", true));
        assertEquals(VideoWallpaperPolicy.Result.TOO_FAST, VideoWallpaperPolicy.validate(
                "x.mp4", 1_000_000L, 3840, 2160, 1000, 120, "video/hevc", true));
        assertEquals(VideoWallpaperPolicy.Result.UNSUPPORTED_CODEC, VideoWallpaperPolicy.validate(
                "x.mp4", 1_000_000L, 3840, 2160, 1000, 60, "video/av01", true));
    }
    @Test public void keepsLegacySurfaceAndUsesNativeUhdPlane() {
        assertFalse(VideoWallpaperPolicy.needsNativeSurface(1920,1080,30,20_000_000));
        assertTrue(VideoWallpaperPolicy.needsNativeSurface(3840,2160,30,60_000_000));
        assertTrue(VideoWallpaperPolicy.needsNativeSurface(3840,2160,60,40_000_000));
        assertTrue(VideoWallpaperPolicy.needsNativeSurface(0,0,0,680L*1024*1024));
    }
    @Test public void reservesStorageWithoutAllocatingVideoInMemory() {
        assertFalse(VideoWallpaperPolicy.enoughStorage(700L*1024*1024,680L*1024*1024));
        assertTrue(VideoWallpaperPolicy.enoughStorage(1300L*1024*1024,680L*1024*1024));
        assertFalse(VideoWallpaperPolicy.enoughStorage(10L*1024*1024*1024,VideoWallpaperPolicy.MAX_BYTES+1));
    }

    @Test public void safeStorageNameIsStableAndDoesNotAcceptPaths() {
        assertTrue(VideoWallpaperPolicy.safeSourceName("fabric-loop.mp4"));
        assertFalse(VideoWallpaperPolicy.safeSourceName("C:\\temp\\fabric-loop.mp4"));
        assertFalse(VideoWallpaperPolicy.safeSourceName(""));
        assertEquals("video-wallpaper-abcdef0123456789.mp4",
                VideoWallpaperPolicy.storageName("fabric-loop.mp4", "abcdef0123456789deadbeef"));
    }
}
