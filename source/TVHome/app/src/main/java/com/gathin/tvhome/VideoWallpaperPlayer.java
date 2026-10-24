package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

/** UI work stays on main; all MediaPlayer calls, including release, share one worker Looper. */
final class VideoWallpaperPlayer implements TextureView.SurfaceTextureListener {
    interface Failure { void onFailure(); }
    private static final String TAG = "TvHome.Video";

    private static final class MediaWorker {
        static final Handler HANDLER;
        static {
            HandlerThread thread = new HandlerThread("TvHome.Media");
            thread.start();
            HANDLER = new Handler(thread.getLooper());
        }
    }

    private final TextureView texture;
    private final File file;
    private final Failure failure;
    private final BackgroundResourceCoordinator.Owner owner;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable startupTimeout;
    private final PlaybackHealth health = new PlaybackHealth();
    private long frames, lastHeartbeat;
    private SerialMediaSession session;
    private boolean active, closed, failed, rendered;
    private int generation, videoWidth, videoHeight;

    VideoWallpaperPlayer(Context context, TextureView texture, File file, Failure failure) {
        this(context, texture, file, BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, failure);
    }

    VideoWallpaperPlayer(Context context, TextureView texture, File file,
                         BackgroundResourceCoordinator.Owner owner, Failure failure) {
        this.texture = texture;
        this.file = file;
        this.failure = failure;
        this.owner = owner == null ? BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER : owner;
        startupTimeout = this::checkPlayback;
        texture.setOpaque(false);
        texture.setAlpha(0.001f);
        texture.setVisibility(TextureView.VISIBLE);
        texture.setSurfaceTextureListener(this);
    }

    void start() {
        if (closed || active) return;
        active = true;
        health.start(SystemClock.elapsedRealtime());
        frames = 0;
        lastHeartbeat = SystemClock.elapsedRealtime();
        failed = false;
        rendered = false;
        texture.setAlpha(0.001f);
        texture.setVisibility(TextureView.VISIBLE);
        armTimeout();
        if (texture.isAvailable()) prepare(texture.getSurfaceTexture());
    }

    void stop() {
        active = false;
        main.removeCallbacks(startupTimeout);
        texture.setAlpha(0f);
        cancelSession();
    }

    void close() {
        if (closed) return;
        closed = true;
        stop();
        // Keep the listener until TextureView destruction. Its SurfaceTexture must outlive queued
        // native release; clearing the listener now could make the UI release that texture early.
    }

    private void armTimeout() {
        main.removeCallbacks(startupTimeout);
        main.postDelayed(startupTimeout, 1_000L);
    }

    private void checkPlayback() {
        if (!active || closed) return;
        PlaybackHealth.Action action = health.poll(SystemClock.elapsedRealtime());
        if (action == PlaybackHealth.Action.FALLBACK) {
            Log.w(TAG, "frame watchdog exhausted; poster fallback owner=" + owner);
            fail();
            return;
        }
        if (action == PlaybackHealth.Action.RETRY) {
            Log.w(TAG, "frame watchdog retry once owner=" + owner + " frames=" + frames);
            cancelSession();
            rendered = false;
            texture.setAlpha(0.001f);
            // Same serial worker: the old decoder must finish release before replacement allocation.
            if (texture.isAvailable()) prepare(texture.getSurfaceTexture());
        }
        armTimeout();
    }

    private void prepare(SurfaceTexture surfaceTexture) {
        if (!active || closed || session != null || surfaceTexture == null) return;
        final int token = ++generation;
        session = new SerialMediaSession(task -> MediaWorker.HANDLER.post(task),
                BackgroundResourceCoordinator.INSTANCE, owner,
                () -> new AndroidBackend(surfaceTexture, file, token), new SerialMediaSession.Listener() {
            @Override public void size(int width, int height) {
                main.post(() -> {
                    if (!isCurrent(token)) return;
                    videoWidth = width;
                    videoHeight = height;
                    updateCrop();
                });
            }
            @Override public void rendered() { main.post(() -> { if (isCurrent(token)) revealFrame(); }); }
            @Override public void failed() { main.post(() -> { if (isCurrent(token)) fail(); }); }
        });
        session.open();
    }

    private boolean isCurrent(int token) { return active && !closed && generation == token && session != null; }

    private void cancelSession() {
        generation++;
        SerialMediaSession old = session;
        session = null;
        if (old != null) old.cancel();
    }

    private void updateCrop() {
        int width = texture.getWidth(), height = texture.getHeight();
        if (width < 1 || height < 1 || videoWidth < 1 || videoHeight < 1) return;
        float scale = Math.max(width / (float) videoWidth, height / (float) videoHeight);
        Matrix transform = new Matrix();
        transform.setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f);
        texture.setTransform(transform);
    }

    private void fail() {
        if (failed || closed) return;
        failed = true;
        stop();
        if (failure != null) failure.onFailure();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { prepare(st); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { updateCrop(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        cancelSession();
        rendered = false;
        texture.setAlpha(active ? 0.001f : 0f);
        // Returning false transfers SurfaceTexture ownership to us. Queue its destruction AFTER
        // decoder disposal, without waiting on main or creating another decoder to bypass a hang.
        MediaWorker.HANDLER.post(st::release);
        if (active && !closed) armTimeout();
        return false;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {
        if (!active || closed || session == null || texture.getSurfaceTexture() != st) return;
        long now = SystemClock.elapsedRealtime();
        health.frame(now);
        frames++;
        revealFrame();
        if (now - lastHeartbeat >= 5_000L) {
            Log.i(TAG, "frame heartbeat owner=" + owner + " generation=" + generation + " frames=" + frames);
            lastHeartbeat = now;
        }
    }

    private void revealFrame() {
        if (!active || closed || rendered) return;
        rendered = true;
        Log.i(TAG, "first frame owner=" + owner + " generation=" + generation);
        texture.setAlpha(1f);
    }

    /** Constructed and used exclusively on MediaWorker, including asynchronous player callbacks. */
    private static final class AndroidBackend implements SerialMediaSession.Backend {
        private final SurfaceTexture texture;
        private final File file;
        private final int token;
        private MediaPlayer player;
        private Surface surface;

        AndroidBackend(SurfaceTexture texture, File file, int token) {
            this.texture = texture;
            this.file = file;
            this.token = token;
        }

        @Override public void prepare(SerialMediaSession.Events events) throws Exception {
            Log.i(TAG, "prepare begin generation=" + token);
            if (!file.isFile()) throw new java.io.FileNotFoundException("Wallpaper source unavailable");
            player = new MediaPlayer();
            surface = new Surface(texture);
            player.setSurface(surface);
            player.setDataSource(file.getPath());
            player.setVolume(0f, 0f);
            player.setLooping(true);
            player.setOnVideoSizeChangedListener((p, w, h) -> events.size(w, h));
            player.setOnPreparedListener(p -> {
                Log.i(TAG, "prepared generation=" + token);
                events.prepared();
            });
            player.setOnCompletionListener(p -> {
                Log.w(TAG, "unexpected completion for looping video generation=" + token);
                events.error();
            });
            player.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "decoder error what=" + what + " extra=" + extra);
                events.error();
                return true;
            });
            player.setOnInfoListener((p, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    events.rendered();
                    return true;
                }
                return false;
            });
            player.prepareAsync();
            Log.i(TAG, "prepare queued generation=" + token);
        }

        @Override public void start() { player.start(); Log.i(TAG, "started generation=" + token); }

        @Override public void release() {
            long began = SystemClock.elapsedRealtime();
            Log.i(TAG, "release begin generation=" + token);
            MediaPlayer old = player;
            player = null;
            try {
                if (old != null) {
                    old.setOnPreparedListener(null);
                    old.setOnErrorListener(null);
                    old.setOnVideoSizeChangedListener(null);
                    old.setOnInfoListener(null);
                    old.setOnCompletionListener(null);
                    old.release();
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "release failed", error);
            } finally {
                if (surface != null) { surface.release(); surface = null; }
                Log.i(TAG, "release end generation=" + token + " elapsedMs=" + (SystemClock.elapsedRealtime() - began));
            }
        }
    }
}
