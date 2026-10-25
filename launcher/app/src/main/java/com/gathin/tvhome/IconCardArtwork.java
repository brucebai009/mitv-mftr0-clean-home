package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The bounded, asynchronous artwork fallback used for ordinary TV apps that do not
 * expose a usable horizontal banner. It deliberately owns every Drawable copy it
 * draws so the PackageManager drawable shown in an ImageView is never re-bound.
 */
public final class IconCardArtwork {
    public static final int STYLE_VERSION = 1;
    public static final int MAX_CACHE_BYTES = 4 * 1024 * 1024;
    private static final int BLUR_RADIUS = 10;
    private static final int BLACK_OVERLAY_ALPHA = 31; // approximately 12%

    private IconCardArtwork() { }

    public static boolean needsFallback(int width, int height) {
        return width <= 0 || height <= 0 || width < height * 1.4f;
    }

    /** Returns a stable opaque colour for the low-resolution fill behind an icon. */
    public static int averageOpaqueColor(int[] pixels) {
        if (pixels == null || pixels.length == 0) return 0xFF444444;
        long r = 0, g = 0, b = 0, count = 0;
        int vivid = 0xFF444444;
        float vividScore = 0f;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha < 32) continue;
            int pr = (pixel >>> 16) & 255, pg = (pixel >>> 8) & 255, pb = pixel & 255;
            float max = Math.max(pr, Math.max(pg, pb));
            float min = Math.min(pr, Math.min(pg, pb));
            float score = max == 0f ? 0f : ((max - min) / max) * (max / 255f);
            if (score > vividScore) {
                vividScore = score;
                vivid = 0xFF000000 | (pr << 16) | (pg << 8) | pb;
            }
            r += pr * alpha;
            g += pg * alpha;
            b += pb * alpha;
            count += alpha;
        }
        if (count == 0) return 0xFF444444;
        if (vividScore >= 0.42f) return vivid | 0xFF000000;
        int average = 0xFF000000 | ((int) (r / count) << 16) | ((int) (g / count) << 8) | (int) (b / count);
        // A wholly black/near-black icon would otherwise make the card disappear.
        return 0xFF000000 | (Math.max(68, (average >>> 16) & 255) << 16)
                | (Math.max(68, (average >>> 8) & 255) << 8) | Math.max(68, average & 255);
    }

    public static String cacheKey(String component, long lastUpdateTime, int width, int height, int style) {
        return String.valueOf(component) + '|' + lastUpdateTime + '|' + width + 'x' + height + '|' + style;
    }

    /** Small LRU measured in actual bitmap allocation bytes, not pixel count. */
    public static final class BitmapCache {
        private final int maxBytes;
        private int bytes;
        private final LinkedHashMap<String, Bitmap> values = new LinkedHashMap<>(16, 0.75f, true);

        public BitmapCache(int maxBytes) { this.maxBytes = Math.max(0, maxBytes); }

        public synchronized Bitmap get(String key) { return values.get(key); }

        public synchronized void put(String key, Bitmap value) {
            if (key == null || value == null || value.isRecycled()) return;
            Bitmap old = values.remove(key);
            if (old != null) bytes -= allocationBytes(old);
            int size = allocationBytes(value);
            if (size > maxBytes) return;
            values.put(key, value);
            bytes += size;
            trimToSize();
        }

        public synchronized int bytes() { return bytes; }

        public synchronized void invalidatePrefix(String prefix) {
            Iterator<Map.Entry<String, Bitmap>> it = values.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Bitmap> entry = it.next();
                if (entry.getKey().startsWith(prefix)) {
                    bytes -= allocationBytes(entry.getValue());
                    it.remove();
                }
            }
            Iterator<Map.Entry<String, Integer>> synthetic = testOnlySizes.entrySet().iterator();
            while (synthetic.hasNext()) {
                Map.Entry<String, Integer> entry = synthetic.next();
                if (entry.getKey().startsWith(prefix)) {
                    bytes -= entry.getValue();
                    synthetic.remove();
                }
            }
        }

        public synchronized void clear() {
            values.clear();
            testOnlySizes.clear();
            bytes = 0;
        }

        // Used only by the plain-JVM contract test when Android's Bitmap factory is not mocked.
        synchronized void putBytesForTest(String key, int size) {
            if (key == null || size < 1 || size > maxBytes) return;
            Integer old = testOnlySizes.remove(key);
            if (old != null) bytes -= old;
            Bitmap oldBitmap = values.remove(key);
            if (oldBitmap != null) bytes -= allocationBytes(oldBitmap);
            testOnlySizes.put(key, size);
            bytes += size;
            trimToSize();
        }

        synchronized boolean containsKeyForTest(String key) {
            return values.containsKey(key) || testOnlySizes.containsKey(key);
        }

        private void trimToSize() {
            Iterator<Map.Entry<String, Bitmap>> it = values.entrySet().iterator();
            while (bytes > maxBytes && it.hasNext()) {
                Map.Entry<String, Bitmap> entry = it.next();
                bytes -= allocationBytes(entry.getValue());
                it.remove();
            }
            Iterator<Map.Entry<String, Integer>> synthetic = testOnlySizes.entrySet().iterator();
            while (bytes > maxBytes && synthetic.hasNext()) {
                bytes -= synthetic.next().getValue();
                synthetic.remove();
            }
        }

        private final Map<String, Integer> testOnlySizes = new LinkedHashMap<>();

        private static int allocationBytes(Bitmap bitmap) {
            try { return Math.max(1, bitmap.getAllocationByteCount()); }
            catch (RuntimeException ignored) { return Math.max(1, bitmap.getRowBytes() * bitmap.getHeight()); }
        }
    }

    public interface Callback { void onReady(Bitmap bitmap); }

    /** One executor and one cache are shared by Dock and the ordinary grid. */
    public static final class Loader {
        private final BitmapCache cache;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "tvhome-icon-art");
            thread.setDaemon(true);
            return thread;
        });
        private final Handler main = new Handler(Looper.getMainLooper());
        private final Map<String, List<Pending>> pending = new LinkedHashMap<>();

        public Loader() { this(new BitmapCache(MAX_CACHE_BYTES)); }
        Loader(BitmapCache cache) { this.cache = cache; }

        public void request(String key, Drawable icon, int width, int height, Callback callback) {
            Bitmap ready = cache.get(key);
            if (ready != null) {
                main.post(() -> callback.onReady(ready));
                return;
            }
            synchronized (pending) {
                List<Pending> waiters = pending.get(key);
                if (waiters != null) {
                    waiters.add(new Pending(callback));
                    return;
                }
                waiters = new ArrayList<>();
                waiters.add(new Pending(callback));
                pending.put(key, waiters);
            }
            Drawable copy = cloneDrawable(icon);
            executor.execute(() -> {
                Bitmap generated = render(copy, width, height);
                if (generated != null) cache.put(key, generated);
                List<Pending> callbacks;
                synchronized (pending) { callbacks = pending.remove(key); }
                if (callbacks == null || generated == null) return;
                for (Pending pendingCallback : callbacks) {
                    main.post(() -> {
                        Callback target = pendingCallback.callback.get();
                        if (target != null) target.onReady(generated);
                    });
                }
            });
        }

        public void shutdown() { executor.shutdownNow(); synchronized (pending) { pending.clear(); } }

        private static final class Pending {
            final WeakReference<Callback> callback;
            Pending(Callback callback) { this.callback = new WeakReference<>(callback); }
        }
    }

    /** Render only a small bitmap; the ImageView will scale this texture to the tile. */
    public static Bitmap render(Drawable source, int width, int height) {
        if (source == null || width < 1 || height < 1 || width > 512 || height > 512) return null;
        Drawable drawable = cloneDrawable(source);
        Bitmap raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] transparent = new int[width * height];
        drawable.setBounds(0, 0, width, height);
        drawable.draw(new Canvas(raw));
        raw.getPixels(transparent, 0, width, 0, 0, width, height);
        int fill = averageOpaqueColor(transparent);
        raw.eraseColor(fill);

        int sourceWidth = Math.max(1, drawable.getIntrinsicWidth());
        int sourceHeight = Math.max(1, drawable.getIntrinsicHeight());
        float scale = Math.max(width / (float) sourceWidth, height / (float) sourceHeight) * 1.15f;
        int drawWidth = Math.max(1, Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, Math.round(sourceHeight * scale));
        Rect bounds = new Rect((width - drawWidth) / 2, (height - drawHeight) / 2,
                (width + drawWidth) / 2, (height + drawHeight) / 2);
        drawable.setBounds(bounds);
        drawable.draw(new Canvas(raw));

        int[] pixels = new int[width * height];
        raw.getPixels(pixels, 0, width, 0, 0, width, height);
        pixels = ArgbBoxBlur.blur(pixels, width, height, Math.min(BLUR_RADIUS, Math.min(width, height) / 2), 2);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (Color.red(p) * (255 - BLACK_OVERLAY_ALPHA)) / 255;
            int g = (Color.green(p) * (255 - BLACK_OVERLAY_ALPHA)) / 255;
            int b = (Color.blue(p) * (255 - BLACK_OVERLAY_ALPHA)) / 255;
            pixels[i] = Color.rgb(r, g, b);
        }
        raw.setPixels(pixels, 0, width, 0, 0, width, height);
        return raw;
    }

    private static Drawable cloneDrawable(Drawable drawable) {
        if (drawable == null) return null;
        Drawable.ConstantState state = drawable.getConstantState();
        return state == null ? drawable.mutate() : state.newDrawable().mutate();
    }
}
