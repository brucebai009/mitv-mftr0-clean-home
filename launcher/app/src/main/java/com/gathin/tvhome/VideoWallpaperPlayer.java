package com.gathin.tvhome;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

/** Home-only muted looping player. It owns no audio focus and releases on every lifecycle stop. */
final class VideoWallpaperPlayer implements TextureView.SurfaceTextureListener {
    interface Failure { void onFailure(); }

    private final TextureView texture;
    private final File file;
    private final Failure failure;
    private MediaPlayer player;
    private Surface surface;
    private boolean active;
    private boolean closed;

    VideoWallpaperPlayer(Context context, TextureView texture, File file, Failure failure) {
        this.texture = texture;
        this.file = file;
        this.failure = failure;
        texture.setOpaque(false);
        texture.setSurfaceTextureListener(this);
        texture.setVisibility(TextureView.GONE);
    }

    void start() {
        if (closed) return;
        if (!BackgroundResourceCoordinator.INSTANCE.acquire(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER)) return;
        active = true;
        if (texture.isAvailable()) prepare(texture.getSurfaceTexture());
    }

    void stop() {
        active = false;
        releasePlayer();
        texture.setVisibility(TextureView.GONE);
        BackgroundResourceCoordinator.INSTANCE.release(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER);
    }

    void close() {
        closed = true;
        stop();
        texture.setSurfaceTextureListener(null);
    }

    private void prepare(SurfaceTexture surfaceTexture) {
        if (!active || closed || player != null || !file.isFile()) return;
        releaseSurface();
        surface = new Surface(surfaceTexture);
        try {
            // The Context constructor was added in API 34; keep the launcher minSdk at 26.
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(file.getPath());
            next.setLooping(true);
            next.setVolume(0f, 0f);
            next.setSurface(surface);
            next.setOnPreparedListener(prepared -> {
                if (!active || closed || player != prepared) {
                    prepared.release();
                    return;
                }
                texture.setVisibility(TextureView.VISIBLE);
                prepared.start();
            });
            next.setOnErrorListener((failed, what, extra) -> {
                releasePlayer();
                BackgroundResourceCoordinator.INSTANCE.release(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER);
                failure.onFailure();
                return true;
            });
            player = next;
            next.prepareAsync();
        } catch (Exception error) {
            releasePlayer();
            BackgroundResourceCoordinator.INSTANCE.release(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER);
            failure.onFailure();
        }
    }

    private void releasePlayer() {
        MediaPlayer old = player;
        player = null;
        if (old != null) {
            try { old.stop(); } catch (Exception ignored) { }
            old.reset();
            old.release();
        }
        releaseSurface();
    }

    private void releaseSurface() {
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        prepare(surface);
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        releasePlayer();
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
}
