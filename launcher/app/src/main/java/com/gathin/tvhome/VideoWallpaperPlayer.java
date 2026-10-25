package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.File;

/** UI work stays on main; all MediaPlayer calls, including release, share one worker Looper. */
final class VideoWallpaperPlayer implements WallpaperVideoSession, TextureView.SurfaceTextureListener {
    interface Failure { void onFailure(); }
    private static final String TAG = "TvHome.Video";

    static final class MediaWorker {
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
    private final PlaybackBookmark bookmark;
    private final boolean looping;
    private final Runnable completion;
    private final ImageView freeze;
    private final String sourceKey;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable startupTimeout;
    private final PlaybackHealth health = new PlaybackHealth();
    private long frames, lastHeartbeat;
    private SerialMediaSession session;
    private boolean active, closed, failed, rendered, hasFrame, readyToRender;
    private int generation, videoWidth, videoHeight;
    private VisualEvents visualEvents;

    @Override public void setVisualEvents(VisualEvents events) { visualEvents = events; }
    @Override public void captureFrame(java.util.function.Consumer<android.graphics.Bitmap> captured) {
        if (closed || !hasFrame || !texture.isAvailable() || texture.getWidth() < 1 || texture.getHeight() < 1) {
            captured.accept(null); return;
        }
        android.graphics.Bitmap frame = null;
        try {
            float scale = Math.min(1f, Math.min(1920f / texture.getWidth(), 1080f / texture.getHeight()));
            frame = texture.getBitmap(Math.max(1, Math.round(texture.getWidth() * scale)),
                    Math.max(1, Math.round(texture.getHeight() * scale)));
        } catch (RuntimeException | OutOfMemoryError unavailable) { /* Keep the previous bridge. */ }
        captured.accept(frame);
    }

    VideoWallpaperPlayer(Context context, TextureView texture, File file, Failure failure) {
        this(context, texture, file, BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, failure,
                true, null, null);
    }

    VideoWallpaperPlayer(Context context, TextureView texture, File file,
                         BackgroundResourceCoordinator.Owner owner, Failure failure) {
        this(context, texture, file, owner, failure, true, null, null);
    }

    VideoWallpaperPlayer(Context context, TextureView texture, File file,
                         BackgroundResourceCoordinator.Owner owner, Failure failure,
                         boolean looping, Runnable completion, PlaybackBookmark suppliedBookmark) {
        this.texture = texture;
        this.file = file;
        this.failure = failure;
        this.owner = owner == null ? BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER : owner;
        this.looping = looping;
        this.completion = completion;
        SharedPreferences resume = context.getApplicationContext().getSharedPreferences(
                "video_resume_" + this.owner.name(), Context.MODE_PRIVATE);
        boolean persistent = this.owner != BackgroundResourceCoordinator.Owner.PREVIEW;
        sourceKey = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        if (suppliedBookmark != null) bookmark = suppliedBookmark;
        else bookmark = new PlaybackBookmark(sourceKey,
                new PlaybackBookmark.Store() {
                    private String source = "";
                    private long position;
                    public String source() { return persistent ? resume.getString("source", "") : source; }
                    public long position() { return persistent ? resume.getLong("position", 0) : position; }
                    public void save(String value, long time) {
                        source = value; position = time;
                        if (persistent) resume.edit().putString("source", value).putLong("position", time).apply();
                    }
                });
        if (this.owner == BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER
                && texture.getParent() instanceof ViewGroup) {
            freeze = new ImageView(context);
            freeze.setScaleType(ImageView.ScaleType.FIT_XY);
            freeze.setImportantForAccessibility(TextureView.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ViewGroup parent = (ViewGroup) texture.getParent();
            parent.addView(freeze, parent.indexOfChild(texture) + 1,
                    new ViewGroup.LayoutParams(texture.getLayoutParams()));
            showFreeze(VideoFreezeFrame.get(sourceKey));
        } else freeze = null;
        startupTimeout = this::checkPlayback;
        texture.setOpaque(false);
        texture.setAlpha(0.001f);
        texture.setVisibility(TextureView.VISIBLE);
        texture.setSurfaceTextureListener(this);
    }

    public void start() {
        if (closed || active) return;
        active = true;
        health.start(SystemClock.elapsedRealtime());
        frames = 0;
        lastHeartbeat = SystemClock.elapsedRealtime();
        failed = false;
        rendered = false;
        readyToRender = false;
        if (!hasFrame) texture.setAlpha(0.001f);
        texture.setVisibility(TextureView.VISIBLE);
        armTimeout();
        if (texture.isAvailable()) prepare(texture.getSurfaceTexture());
    }

    public void stop() {
        if (active && rendered && readyToRender && hasFrame && freeze != null)
            showFreeze(VideoFreezeFrame.capture(sourceKey, texture));
        active = false;
        main.removeCallbacks(startupTimeout);
        // Keep the full-resolution last texture. Do not expose the small catalogue thumbnail.
        // Disposal records the position on the media worker, then frees the decoder for other apps.
        cancelSession();
    }

    private void showFreeze(android.graphics.Bitmap bitmap) {
        if (freeze == null) return;
        freeze.setImageBitmap(bitmap);
        freeze.setVisibility(bitmap == null ? TextureView.GONE : TextureView.VISIBLE);
    }

    public void close() {
        if (closed) return;
        closed = true;
        stop();
        // Keep the listener until TextureView destruction. Its SurfaceTexture must outlive queued
        // native release; clearing the listener now could make the UI release that texture early.
    }

    @Override public void closeWhenReleased(Runnable afterRelease) {
        if (closed) {
            if (afterRelease != null) main.post(afterRelease);
            return;
        }
        closed = true;
        active = false;
        main.removeCallbacks(startupTimeout);
        cancelSession(afterRelease);
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
            if (rendered && readyToRender && hasFrame && freeze != null)
                showFreeze(VideoFreezeFrame.capture(sourceKey, texture));
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
                () -> new AndroidBackend(surfaceTexture, file, token, bookmark), new SerialMediaSession.Listener() {
            @Override public void started() { main.post(() -> { if (isCurrent(token)) readyToRender = true; }); }
            @Override public void size(int width, int height) {
                main.post(() -> {
                    if (!isCurrent(token)) return;
                    videoWidth = width;
                    videoHeight = height;
                    updateCrop();
                });
            }
            @Override public void rendered() { main.post(() -> { if (isCurrent(token)) revealFrame(); }); }
            @Override public void completed() { main.post(() -> {
                if (isCurrent(token)) {
                    main.removeCallbacks(startupTimeout);
                    if (completion != null) completion.run();
                }
            }); }
            @Override public void failed() { main.post(() -> { if (isCurrent(token)) fail(); }); }
        });
        session.open();
    }

    private boolean isCurrent(int token) { return active && !closed && generation == token && session != null; }

    private void cancelSession() {
        cancelSession(null);
    }

    private void cancelSession(Runnable afterRelease) {
        generation++;
        readyToRender = false;
        SerialMediaSession old = session;
        session = null;
        if (old != null) old.cancel(afterRelease);
        else if (afterRelease != null) main.post(afterRelease);
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
        texture.setAlpha(0f);
        if (failure != null) failure.onFailure();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { prepare(st); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { updateCrop(); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (active && rendered && readyToRender && hasFrame && freeze != null)
            showFreeze(VideoFreezeFrame.capture(sourceKey, texture));
        cancelSession();
        rendered = false;
        hasFrame = false;
        readyToRender = false;
        texture.setAlpha(active ? 0.001f : 0f);
        // Returning false transfers SurfaceTexture ownership to us. Queue its destruction AFTER
        // decoder disposal, without waiting on main or creating another decoder to bypass a hang.
        MediaWorker.HANDLER.post(st::release);
        if (active && !closed) armTimeout();
        return false;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {
        if (!active || closed || !readyToRender || session == null || texture.getSurfaceTexture() != st) return;
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
        if (!active || closed || !readyToRender || rendered) return;
        rendered = true;
        hasFrame = true;
        Log.i(TAG, "first frame owner=" + owner + " generation=" + generation);
        texture.setAlpha(1f);
        if (freeze != null) { showFreeze(null); VideoFreezeFrame.clear(sourceKey); }
        if (visualEvents != null) visualEvents.firstFrame();
    }

    /** Constructed and used exclusively on MediaWorker, including asynchronous player callbacks. */
    private final class AndroidBackend implements SerialMediaSession.Backend {
        private final SurfaceTexture texture;
        private final File file;
        private final int token;
        private final PlaybackBookmark bookmark;
        private final boolean looping;
        private boolean prepared, started, completed;
        private MediaPlayer player;
        private Surface surface;
        private final VideoEndCue endCue = new VideoEndCue(MediaWorker.HANDLER);

        AndroidBackend(SurfaceTexture texture, File file, int token, PlaybackBookmark bookmark) {
            this.texture = texture;
            this.file = file;
            this.token = token;
            this.bookmark = bookmark;
            this.looping = VideoWallpaperPlayer.this.looping;
        }

        @Override public void prepare(SerialMediaSession.Events events) throws Exception {
            Log.i(TAG, "prepare begin generation=" + token);
            if (!file.isFile()) throw new java.io.FileNotFoundException("Wallpaper source unavailable");
            player = new MediaPlayer();
            surface = new Surface(texture);
            player.setSurface(surface);
            player.setDataSource(file.getPath());
            player.setVolume(0f, 0f);
            player.setLooping(looping);
            player.setOnVideoSizeChangedListener((p, w, h) -> events.size(w, h));
            player.setOnPreparedListener(p -> {
                if (p != player) return;
                try {
                    prepared = true;
                    long position = bookmark.restore(p.getDuration());
                    Log.i(TAG, "prepared generation=" + token + " resumeMs=" + position);
                    if (position == 0) events.prepared();
                    else {
                        p.setOnSeekCompleteListener(seeked -> {
                            if (seeked != player) return;
                            try {
                                seeked.setOnSeekCompleteListener(null);
                                Log.i(TAG, "resume seek complete generation=" + token + " positionMs=" + seeked.getCurrentPosition());
                                events.prepared();
                            } catch (RuntimeException unavailable) { events.error(); }
                        });
                        p.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                    }
                } catch (RuntimeException unavailable) { events.error(); }
            });
            player.setOnCompletionListener(p -> {
                endCue.cancel();
                if (looping) {
                    Log.w(TAG, "unexpected completion for looping video generation=" + token);
                    events.error();
                } else {
                    completed = true;
                    Log.i(TAG, "natural completion generation=" + token);
                    events.completed();
                }
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

        @Override public void start() {
            player.start(); started = true;
            if (!looping) endCue.start(() -> (long) player.getDuration() - player.getCurrentPosition(),
                    () -> main.post(() -> {
                        if (isCurrent(token) && visualEvents != null) visualEvents.approachingEnd();
                    }));
            Log.i(TAG, "started generation=" + token);
        }

        @Override public void release() {
            endCue.cancel();
            long began = SystemClock.elapsedRealtime();
            Log.i(TAG, "release begin generation=" + token);
            MediaPlayer old = player;
            player = null;
            try {
                if (old != null) {
                    if (prepared && started && !completed) {
                        try {
                            old.pause();
                            long position = old.getCurrentPosition();
                            bookmark.remember(position, old.getDuration(), true);
                            Log.i(TAG, "saved position generation=" + token + " positionMs=" + position);
                        } catch (RuntimeException error) { Log.w(TAG, "keeping prior bookmark", error); }
                    }
                    old.setOnPreparedListener(null);
                    old.setOnErrorListener(null);
                    old.setOnVideoSizeChangedListener(null);
                    old.setOnInfoListener(null);
                    old.setOnCompletionListener(null);
                    old.setOnSeekCompleteListener(null);
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
