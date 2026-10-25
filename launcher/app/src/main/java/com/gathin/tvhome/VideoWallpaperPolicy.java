package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation policy for the additive, private video-wallpaper mode. */
final class VideoWallpaperPolicy {
    static final long MAX_BYTES = 2L * 1024L * 1024L * 1024L;
    static final long MAX_DURATION_MS = 3_600_000L;
    static final int MAX_WIDTH = 3_840;
    static final int MAX_HEIGHT = 2_160;
    static final int MAX_FPS = 60;

    enum Result { VALID, BAD_NAME, BAD_FORMAT, TOO_LARGE, TOO_LONG, TOO_WIDE, TOO_TALL, TOO_FAST, NOT_H264, UNSUPPORTED_CODEC, NOT_SDR }

    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_.-]+\\.mp4", Pattern.CASE_INSENSITIVE);

    private VideoWallpaperPolicy() { }

    static Result validate(String sourceName, long bytes, int width, int height, long durationMs,
                           int fps, boolean h264, boolean sdr) {
        return validate(sourceName, bytes, width, height, durationMs, fps,
                h264 ? "video/avc" : "legacy/unsupported", sdr);
    }

    static Result validate(String sourceName, long bytes, int width, int height, long durationMs,
                           int fps, String mime, boolean sdr) {
        if (!safeSourceName(sourceName)) return sourceName != null && sourceName.toLowerCase(Locale.ROOT).endsWith(".mp4")
                ? Result.BAD_NAME : Result.BAD_FORMAT;
        if (bytes <= 0 || bytes > MAX_BYTES) return Result.TOO_LARGE;
        if (width <= 0 || width > MAX_WIDTH) return Result.TOO_WIDE;
        if (height <= 0 || height > MAX_HEIGHT) return Result.TOO_TALL;
        if (durationMs <= 0 || durationMs > MAX_DURATION_MS) return Result.TOO_LONG;
        if (fps <= 0 || fps > MAX_FPS) return Result.TOO_FAST;
        if ("legacy/unsupported".equals(mime)) return Result.NOT_H264;
        if (!"video/avc".equals(mime) && !"video/hevc".equals(mime)) return Result.UNSUPPORTED_CODEC;
        if (!sdr) return Result.NOT_SDR;
        return Result.VALID;
    }

    static boolean needsNativeSurface(int width, int height, int fps, long bytes) {
        return width > 1920 || height > 1080 || fps > 30 || bytes > 100L * 1024 * 1024;
    }

    static boolean enoughStorage(long available, long incoming) {
        // Keep 512 MiB for system/app work after importing. No whole-file memory allocation.
        return incoming > 0 && incoming <= MAX_BYTES && available >= incoming + 512L * 1024 * 1024;
    }

    static boolean safeSourceName(String name) {
        return name != null && SOURCE.matcher(name).matches();
    }

    static String storageName(String sourceName, String digest) {
        String safe = digest == null ? "" : digest.replaceAll("[^0-9a-fA-F]", "").toLowerCase(Locale.ROOT);
        if (safe.length() > 16) safe = safe.substring(0, 16);
        if (safe.isEmpty()) safe = "video";
        return "video-wallpaper-" + safe + ".mp4";
    }
}
