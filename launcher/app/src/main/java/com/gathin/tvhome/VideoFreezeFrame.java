package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.view.TextureView;

/** One UI-thread cache, at most 1080p. No per-frame copying, blur, disk write or second decoder. */
final class VideoFreezeFrame {
    private static String key;
    private static Bitmap frame;

    static Bitmap get(String source) { return source.equals(key) ? frame : null; }

    static Bitmap capture(String source, TextureView texture) {
        if (!texture.isAvailable() || texture.getWidth() <= 0 || texture.getHeight() <= 0) return get(source);
        float scale = Math.min(1f, Math.min(1920f / texture.getWidth(), 1080f / texture.getHeight()));
        try {
            Bitmap next = texture.getBitmap(Math.max(1, Math.round(texture.getWidth() * scale)),
                    Math.max(1, Math.round(texture.getHeight() * scale)));
            if (next != null) { key = source; frame = next; }
        } catch (RuntimeException | OutOfMemoryError unavailable) { /* Keep the retained texture. */ }
        return get(source);
    }

    static void clear(String source) {
        if (source.equals(key)) { key = null; frame = null; }
    }
}
