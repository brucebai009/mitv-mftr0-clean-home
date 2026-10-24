package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** One-shot API30-safe settings background blur. It never captures text or another app. */
public final class SettingsBackdropRenderer implements AutoCloseable {
    private final ImageView target;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private boolean closed;

    public SettingsBackdropRenderer(ImageView target) { this.target = target; }

    public void showOwnedSnapshot(Bitmap snapshot, int radius) {
        if (snapshot == null) return;
        if (closed) { snapshot.recycle(); return; }
        if (snapshot.isRecycled() || snapshot.getWidth() > 640 || snapshot.getHeight() > 360) {
            throw new IllegalArgumentException("Pass an owned <=640x360 wallpaper snapshot");
        }
        final int token = generation.incrementAndGet();
        worker.execute(() -> {
            Bitmap result = null;
            try {
                int w = snapshot.getWidth(), h = snapshot.getHeight();
                int[] pixels = new int[w * h]; snapshot.getPixels(pixels, 0, w, 0, 0, w, h);
                result = Bitmap.createBitmap(ArgbBoxBlur.blur(pixels, w, h, Math.max(0, Math.min(8, radius)), 3),
                        w, h, Bitmap.Config.ARGB_8888);
            } catch (RuntimeException | OutOfMemoryError ignored) {
                // Keep the poster/current background instead of flashing black or crashing Settings.
            } finally { snapshot.recycle(); }
            final Bitmap ready = result;
            if (ready == null) return;
            if (!target.post(() -> {
                if (closed || generation.get() != token) { ready.recycle(); return; }
                target.setImageBitmap(ready);
            })) ready.recycle();
        });
    }

    @Override public void close() {
        if (closed) return;
        closed = true; generation.incrementAndGet(); worker.shutdown(); target.setImageDrawable(null);
    }
}
