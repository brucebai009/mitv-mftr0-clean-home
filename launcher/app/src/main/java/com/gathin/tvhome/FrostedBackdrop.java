package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One small snapshot per modal stack. The modal lives in another Window and stays sharp. */
final class FrostedBackdrop {
    static final int MAX_WIDTH = 384, MAX_HEIGHT = 216;
    static volatile int activeLayers;
    static volatile long completedCaptures, lastBlurMs;
    static volatile String lastResult = "NOT_RUN";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 15,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(2), task -> {
        Thread thread = new Thread(task, "TvHome.ModalBlur"); thread.setDaemon(true); return thread;
    });
    static { WORKER.allowCoreThreadTimeOut(true); }

    private FrostedBackdrop() { }

    static Lease acquire(Activity activity) {
        return acquire((ViewGroup) activity.getWindow().getDecorView(), WORKER);
    }

    static Lease acquire(ViewGroup root, Executor worker) {
        State state = (State) root.getTag(R.id.modalBackdropState);
        if (state == null || state.closed) {
            state = new State(root);
            root.setTag(R.id.modalBackdropState, state);
            state.install(worker);
        }
        state.references++;
        return new Lease(state);
    }

    static final class Lease implements AutoCloseable {
        private State state;
        Lease(State state) { this.state = state; }
        @Override public void close() {
            State old = state; state = null;
            if (old != null && !old.closed && --old.references == 0) old.close();
        }
    }

    private static final class State implements View.OnAttachStateChangeListener {
        final ViewGroup root;
        final ImageView image;
        int references;
        boolean closed;
        BlurJob job;
        State(ViewGroup root) {
            this.root = root;
            image = new ImageView(root.getContext());
            image.setId(R.id.modalFrostedBackdrop);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setFocusable(false); image.setClickable(false);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            // A capture failure still must not leave two layers of readable text superimposed.
            image.setBackgroundColor(0xFF252B32);
        }
        void install(Executor worker) {
            Bitmap snapshot = null;
            try {
                int width = root.getWidth(), height = root.getHeight();
                if (width > 0 && height > 0) {
                    float scale = Math.min(1f, Math.min(MAX_WIDTH / (float) width, MAX_HEIGHT / (float) height));
                    snapshot = Bitmap.createBitmap(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(snapshot);
                    canvas.drawColor(0xFF252B32); canvas.scale(scale, scale);
                    // Capture before adding the overlay, never the dialog or a previous blur layer.
                    root.draw(canvas);
                }
            } catch (RuntimeException | OutOfMemoryError unavailable) {
                if (snapshot != null) snapshot.recycle(); snapshot = null;
            }
            root.addView(image, new ViewGroup.LayoutParams(-1, -1));
            root.addOnAttachStateChangeListener(this);
            activeLayers++; lastResult = "CAPTURING";
            if (snapshot == null) { lastResult = "OPAQUE_FALLBACK"; return; }
            job = new BlurJob(this, snapshot);
            try { worker.execute(job); }
            catch (RuntimeException unavailable) { job.cancel(); lastResult = "OPAQUE_FALLBACK"; }
        }
        void receive(Bitmap bitmap, long elapsed) {
            if (closed || root.getTag(R.id.modalBackdropState) != this) {
                if (bitmap != null) bitmap.recycle(); return;
            }
            if (bitmap == null) { lastResult = "OPAQUE_FALLBACK"; return; }
            BitmapDrawable drawable = new BitmapDrawable(root.getResources(), bitmap);
            drawable.setFilterBitmap(true); image.setImageDrawable(drawable);
            completedCaptures++; lastBlurMs = elapsed; lastResult = "FROSTED";
        }
        void close() {
            if (closed) return;
            closed = true;
            if (job != null) { WORKER.remove(job); job.cancel(); job = null; }
            root.removeOnAttachStateChangeListener(this);
            root.removeView(image); image.setImageDrawable(null);
            // RenderThread may still own a displayed bitmap. Release references, do not recycle it.
            if (root.getTag(R.id.modalBackdropState) == this) root.setTag(R.id.modalBackdropState, null);
            activeLayers--; lastResult = "CLOSED";
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) { close(); }
    }

    private static final class BlurJob implements Runnable {
        final State owner;
        private Bitmap input;
        private volatile boolean cancelled;
        BlurJob(State owner, Bitmap input) { this.owner = owner; this.input = input; }
        synchronized void cancel() {
            cancelled = true;
            if (input != null) { input.recycle(); input = null; }
        }
        private synchronized Bitmap takeInput() { Bitmap value = input; input = null; return value; }
        @Override public void run() {
            Bitmap captured = takeInput();
            if (captured == null) return;
            Bitmap result = null;
            long started = SystemClock.elapsedRealtime();
            try {
                if (!cancelled) {
                    int width = captured.getWidth(), height = captured.getHeight();
                    int[] pixels = new int[width * height];
                    captured.getPixels(pixels, 0, width, 0, 0, width, height);
                    int[] blurred = ArgbBoxBlur.blur(pixels, width, height, 9, 3);
                    if (!cancelled) {
                        result = Bitmap.createBitmap(blurred, width, height, Bitmap.Config.ARGB_8888);
                        new Canvas(result).drawColor(Color.argb(32, 24, 30, 38));
                    }
                }
            } catch (RuntimeException | OutOfMemoryError unavailable) { /* Opaque material remains. */ }
            finally { captured.recycle(); }
            final Bitmap ready = result;
            final long elapsed = SystemClock.elapsedRealtime() - started;
            MAIN.post(() -> owner.receive(ready, elapsed));
        }
    }
}
