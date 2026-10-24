package com.gathin.tvhome;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation policy for the additive, private video-wallpaper mode. */
final class VideoWallpaperPolicy {
    static final long MAX_BYTES = 100L * 1024L * 1024L;
    static final long MAX_DURATION_MS = 120_000L;
    static final int MAX_WIDTH = 1_920;
    static final int MAX_HEIGHT = 1_080;
    static final int MAX_FPS = 30;

    enum Result { VALID, BAD_NAME, BAD_FORMAT, TOO_LARGE, TOO_LONG, TOO_WIDE, TOO_TALL, TOO_FAST, NOT_H264, NOT_SDR }

    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9_.-]+\\.mp4", Pattern.CASE_INSENSITIVE);

    private VideoWallpaperPolicy() { }

    static Result validate(String sourceName, long bytes, int width, int height, long durationMs,
                           int fps, boolean h264, boolean sdr) {
        if (!safeSourceName(sourceName)) return sourceName != null && sourceName.toLowerCase(Locale.ROOT).endsWith(".mp4")
                ? Result.BAD_NAME : Result.BAD_FORMAT;
        if (bytes <= 0 || bytes > MAX_BYTES) return Result.TOO_LARGE;
        if (width <= 0 || width > MAX_WIDTH) return Result.TOO_WIDE;
        if (height <= 0 || height > MAX_HEIGHT) return Result.TOO_TALL;
        if (durationMs <= 0 || durationMs > MAX_DURATION_MS) return Result.TOO_LONG;
        if (fps <= 0 || fps > MAX_FPS) return Result.TOO_FAST;
        if (!h264) return Result.NOT_H264;
        if (!sdr) return Result.NOT_SDR;
        return Result.VALID;
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
