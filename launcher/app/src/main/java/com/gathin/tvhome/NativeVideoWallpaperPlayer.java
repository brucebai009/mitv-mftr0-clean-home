package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import java.io.File;

/** UHD uses a separate native video plane; a 1080p TV UI must not downsample it into a TextureView.
 * All decoder work shares the existing serial worker and lease with legacy videos and previews.
 */
final class NativeVideoWallpaperPlayer implements WallpaperVideoSession, SurfaceHolder.Callback {
    private static final String TAG = "TvHome.UHD";
    private final SurfaceView view;
    private final ImageView cover;
    private final File file;
    private final PlaybackBookmark bookmark;
    private final boolean looping;
    private final Runnable completion;
    private final Runnable failure;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PlaybackHealth health = new PlaybackHealth();
    private final Runnable watchdog = this::check;
    private final android.graphics.drawable.Drawable poster;
    private SerialMediaSession session;
    private boolean active, closed, rendered, ready, visualNotified;
    private VisualEvents visualEvents;
    private int generation;

    NativeVideoWallpaperPlayer(Context context, SurfaceView view, ImageView cover, File file) {
        this(context, view, cover, file, true, null, null, null);
    }

    NativeVideoWallpaperPlayer(Context context, SurfaceView view, ImageView cover, File file,
                               boolean looping, Runnable completion, PlaybackBookmark suppliedBookmark,
                               Runnable failure) {
        this.view = view; this.cover = cover; this.file = file; this.poster = cover.getDrawable();
        this.looping = looping; this.completion = completion; this.failure = failure;
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(
                "video_resume_VIDEO_WALLPAPER", Context.MODE_PRIVATE);
        String key = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        if (suppliedBookmark != null) bookmark = suppliedBookmark;
        else bookmark = new PlaybackBookmark(key, new PlaybackBookmark.Store() {
            public String source() { return preferences.getString("source", ""); }
            public long position() { return preferences.getLong("position", 0); }
            public void save(String source, long position) {
                preferences.edit().putString("source", source).putLong("position", position).apply();
            }
        });
        view.setZOrderOnTop(false);
        view.getHolder().addCallback(this);
        view.setVisibility(View.GONE);
    }
    @Override public void start() {
        if (closed || active) return;
        active = true; ready = false; rendered = false; visualNotified = false;
        health.start(SystemClock.elapsedRealtime());
        cover.setVisibility(View.VISIBLE);
        view.setVisibility(View.VISIBLE);
        prepare();
        main.postDelayed(watchdog, 1000);
    }
    @Override public void stop() {
        if (active && rendered) captureOnce();
        active = false;
        main.removeCallbacks(watchdog);
        cancel();
        // Keep the submitted last buffer behind a clear snapshot; no software blur or background decoder.
    }
    @Override public void close() {
        if (closed) return;
        stop(); closed = true;
        view.getHolder().removeCallback(this);
    }
    @Override public void closeWhenReleased(Runnable afterRelease) {
        if (closed) { if (afterRelease != null) main.post(afterRelease); return; }
        active = false;
        main.removeCallbacks(watchdog);
        closed = true;
        cancel(afterRelease);
        view.getHolder().removeCallback(this);
    }
    private void captureOnce() {
        if (!view.getHolder().getSurface().isValid() || view.getWidth() < 1 || view.getHeight() < 1) return;
        final int token = generation;
        try {
            float scale = Math.min(1f, Math.min(1920f/view.getWidth(), 1080f/view.getHeight()));
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(view.getWidth()*scale)),
                    Math.max(1, Math.round(view.getHeight()*scale)), Bitmap.Config.ARGB_8888);
            PixelCopy.request(view, bitmap, result -> {
                // stop increments generation once. Ignore callbacks after a new start or source change.
                if (result == PixelCopy.SUCCESS && !active && !closed && generation == token + 1) {
                    cover.setImageBitmap(bitmap); cover.setVisibility(View.VISIBLE);
                } else bitmap.recycle();
            }, main);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            Log.w(TAG, "Snapshot unavailable; retain the last surface buffer");
        }
    }
    @Override public void setVisualEvents(VisualEvents events) { visualEvents = events; }
    @Override public void captureFrame(java.util.function.Consumer<Bitmap> captured) {
        if (closed || !rendered || !view.getHolder().getSurface().isValid()
                || view.getWidth() < 1 || view.getHeight() < 1) { captured.accept(null); return; }
        final int token = generation;
        Bitmap allocated = null;
        try {
            float scale = Math.min(1f, Math.min(1920f / view.getWidth(), 1080f / view.getHeight()));
            final Bitmap frame = Bitmap.createBitmap(Math.max(1, Math.round(view.getWidth() * scale)),
                    Math.max(1, Math.round(view.getHeight() * scale)), Bitmap.Config.ARGB_8888);
            allocated = frame;
            PixelCopy.request(view, frame, result -> {
                if (result == PixelCopy.SUCCESS && !closed && generation == token) captured.accept(frame);
                else { frame.recycle(); captured.accept(null); }
            }, main);
        } catch (RuntimeException | OutOfMemoryError unavailable) {
            if (allocated != null) allocated.recycle();
            captured.accept(null);
        }
    }
    private void cancel() { cancel(null); }
    private void cancel(Runnable afterRelease) {
        generation++; ready = false; visualNotified = false;
        SerialMediaSession old = session; session = null;
        if (old != null) old.cancel(afterRelease);
        else if (afterRelease != null) main.post(afterRelease);
    }
    private boolean current(int token) { return active && !closed && generation == token && session != null; }
    private void prepare() {
        if (!active || closed || session != null || !view.getHolder().getSurface().isValid()) return;
        final int token = ++generation;
        final Surface surface = view.getHolder().getSurface();
        session = new SerialMediaSession(task -> VideoWallpaperPlayer.MediaWorker.HANDLER.post(task),
                BackgroundResourceCoordinator.INSTANCE, BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER,
                () -> new Backend(surface, token), new SerialMediaSession.Listener() {
            @Override public void started() { main.post(() -> { if (current(token)) { ready = true; reveal(); } }); }
            @Override public void size(int w, int h) { main.post(() -> {
                if (!current(token)) return;
                view.getHolder().setFixedSize(w, h);
                Log.i(TAG, "nativeBuffer=" + w + "x" + h + " ui=" + view.getWidth() + "x" + view.getHeight());
            }); }
            @Override public void rendered() { main.post(() -> { if (current(token)) {
                rendered = true; health.frame(SystemClock.elapsedRealtime()); reveal();
            } }); }
            @Override public void completed() { main.post(() -> {
                if (current(token)) {
                    main.removeCallbacks(watchdog);
                    if (completion != null) completion.run();
                }
            }); }
            @Override public void failed() { main.post(() -> { if (current(token)) fail("decoder/capability"); }); }
        });
        session.open();
    }
    private void reveal() {
        if (active && ready && rendered) {
            cover.setVisibility(View.GONE);
            if (!visualNotified) {
                visualNotified = true;
                if (visualEvents != null) visualEvents.firstFrame();
            }
        }
    }
    private void fail(String reason) {
        Log.e(TAG, "UHD_FAILED " + reason);
        stop(); rendered = false;
        cover.setImageDrawable(poster); cover.setVisibility(View.VISIBLE);
        view.setVisibility(View.GONE);
        if (failure != null) failure.run();
        else android.widget.Toast.makeText(view.getContext(), "此视频未能按原规格播放，已显示静态封面", android.widget.Toast.LENGTH_LONG).show();
    }
    private void check() {
        if (!active || closed) return;
        PlaybackHealth.Action action = health.poll(SystemClock.elapsedRealtime());
        if (action == PlaybackHealth.Action.FALLBACK) { fail("watchdog exhausted"); return; }
        if (action == PlaybackHealth.Action.RETRY) {
            Log.w(TAG, "watchdog retry once");
            captureOnce(); cancel(); rendered = false; ready = false; prepare();
        }
        main.postDelayed(watchdog, 1000);
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { prepare(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { prepare(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        cancel(); rendered = false;
        cover.setVisibility(View.VISIBLE);
    }

    private final class Backend implements SerialMediaSession.Backend {
        final Surface surface;
        final int token;
        MediaPlayer player;
        boolean prepared, started, completed;
        int previousPosition = -1;
        final Runnable heartbeat = this::sample;
        final VideoEndCue endCue = new VideoEndCue(VideoWallpaperPlayer.MediaWorker.HANDLER);
        Backend(Surface surface, int token) { this.surface = surface; this.token = token; }
        @Override public void prepare(SerialMediaSession.Events events) throws Exception {
            VideoSourceInfo info = VideoSourceInfo.inspect(file);
            VideoWallpaperPolicy.Result result = VideoWallpaperPolicy.validate(file.getName(), file.length(),
                    info.width, info.height, info.durationMs, info.fps, info.mime, info.sdr);
            if (result != VideoWallpaperPolicy.Result.VALID || !info.canPlayOriginal(view.getContext(), file))
                throw new java.io.IOException("Original media unsupported: " + result);
            Log.i(TAG, "source=" + info.json().toString());
            if (Build.VERSION.SDK_INT >= 30) try {
                surface.setFrameRate(info.fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
            } catch (RuntimeException unsupported) { Log.w(TAG, "Frame-rate hint unsupported; verify physical output"); }
            player = new MediaPlayer();
            player.setSurface(surface); player.setDataSource(file.getPath());
            player.setVolume(0f, 0f); player.setLooping(looping);
            player.setOnVideoSizeChangedListener((p,w,h) -> events.size(w,h));
            player.setOnPreparedListener(p -> {
                if (p != player) return;
                try {
                    prepared = true;
                    long position = bookmark.restore(p.getDuration());
                    Log.i(TAG, "prepared token=" + token + " resumeMs=" + position);
                    if (position == 0) events.prepared();
                    else {
                        p.setOnSeekCompleteListener(seeked -> {
                            if (seeked != player) return;
                            seeked.setOnSeekCompleteListener(null); events.prepared();
                        });
                        p.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                    }
                } catch (RuntimeException error) { events.error(); }
            });
            player.setOnErrorListener((p,what,extra) -> { Log.e(TAG,"decode error="+what+"/"+extra); events.error(); return true; });
            player.setOnCompletionListener(p -> {
                endCue.cancel();
                VideoWallpaperPlayer.MediaWorker.HANDLER.removeCallbacks(heartbeat);
                if (looping) events.error();
                else { completed = true; Log.i(TAG, "natural completion token=" + token); events.completed(); }
            });
            player.setOnInfoListener((p,what,extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) { events.rendered(); return true; }
                return false;
            });
            player.prepareAsync();
        }
        @Override public void start() {
            player.start(); started = true;
            if (!looping) endCue.start(() -> (long) player.getDuration() - player.getCurrentPosition(),
                    () -> main.post(() -> {
                        if (current(token) && visualEvents != null) visualEvents.approachingEnd();
                    }));
            VideoWallpaperPlayer.MediaWorker.HANDLER.postDelayed(heartbeat, 2000);
        }
        private void sample() {
            if (player == null || !started) return;
            try {
                int position = player.getCurrentPosition();
                PersistableBundle metrics = player.getMetrics();
                org.json.JSONObject values = new org.json.JSONObject();
                if (metrics != null) for (String key : metrics.keySet()) {
                    try { values.put(key, metrics.get(key)); } catch (org.json.JSONException ignored) { }
                }
                Log.i(TAG, "sample token="+token+" positionMs="+position+" metrics="+values);
                // Position is only a liveness guard; it is NOT proof of displayed 60 fps.
                // The device verifier additionally measures this SurfaceFlinger layer.
                if (position != previousPosition) main.post(() -> {
                    if (current(token)) health.frame(SystemClock.elapsedRealtime());
                });
                previousPosition = position;
            } catch (RuntimeException unavailable) { Log.w(TAG, "Metrics unavailable"); }
            VideoWallpaperPlayer.MediaWorker.HANDLER.postDelayed(heartbeat, 2000);
        }
        @Override public void release() {
            endCue.cancel();
            VideoWallpaperPlayer.MediaWorker.HANDLER.removeCallbacks(heartbeat);
            MediaPlayer old = player; player = null;
            if (old == null) return;
            try {
                if (prepared && started && !completed) {
                    try {
                        old.pause(); long position = old.getCurrentPosition();
                        bookmark.remember(position, old.getDuration(), true);
                        Log.i(TAG, "saved positionMs=" + position + " token=" + token);
                    } catch (RuntimeException ignored) { }
                }
                old.setOnPreparedListener(null); old.setOnErrorListener(null); old.setOnInfoListener(null);
                old.setOnVideoSizeChangedListener(null); old.setOnCompletionListener(null); old.setOnSeekCompleteListener(null);
                old.release();
            } catch (RuntimeException releaseError) {
                Log.w(TAG, "Decoder release failed", releaseError);
            } finally {
                // SurfaceHolder owns this Surface; never release it from a stale decoder generation.
                Log.i(TAG, "released token=" + token);
            }
        }
    }
}
