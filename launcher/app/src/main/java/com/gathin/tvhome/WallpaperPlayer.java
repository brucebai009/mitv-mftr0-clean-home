package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer implements WallpaperPlaylistController.Host {
    private final Context context;
    private final FrameLayout root;
    /** Pure background container; UI, focus and dialogs stay above this layer. */
    private final FrameLayout backgroundRoot;
    private final ImageView image;
    private final View dissolveOverlay;
    private final WallpaperDissolve dissolve;
    private WallpaperVideoSession visibleSession;
    private final List<File> files;
    private final long interval;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    private final int height;
    private final SharedPreferences settings;
    private WallpaperVideoSession videoPlayer;
    private final Map<WallpaperVideoSession, List<View>> playlistViews = new HashMap<>();
    private final WallpaperPlaylistController playlistController;
    private final boolean videoMode;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private int playlistPosterGeneration;
    private String displayed = "";

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
        this.context = context.getApplicationContext();
        this.root = root;
        FrameLayout existing = root.findViewById(R.id.backgroundRoot);
        if (existing != null) {
            backgroundRoot = existing;
        } else {
            backgroundRoot = new FrameLayout(context);
            backgroundRoot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            root.addView(backgroundRoot, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        backgroundRoot.setClipChildren(false);
        files = new ArrayList<>(config.wallpapers);
        interval = config.wallpaperRotationMinutes * 60_000L;
        preferences = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE);
        settings = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels;
        height = metrics.heightPixels;
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        WallpaperPlaylistStore playlistStore = new WallpaperPlaylistStore(context);
        List<WallpaperCandidate> visibleVideos = WallpaperCatalog.visibleVideos(Config.dir(context));
        List<String> selectedIds = WallpaperPlaylistPolicy.normalizeSelection(visibleVideos, playlistStore.ids());
        boolean playlistMode = !settings.getBoolean("static_cover", false)
                && playlistStore.enabled() && !selectedIds.isEmpty();
        dissolveOverlay = playlistMode ? new View(context) : null;
        dissolve = playlistMode ? new WallpaperDissolve(dissolveOverlay,
                config.animations, config.reducedMotion) : null;
        if (dissolveOverlay != null) {
            dissolveOverlay.setFocusable(false); dissolveOverlay.setClickable(false);
            dissolveOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            backgroundRoot.addView(dissolveOverlay, new FrameLayout.LayoutParams(-1, -1));
        }
        if ("video".equals(settings.getString("background_mode", "image")) || playlistMode) {
            File poster = playlistMode ? posterFor(context, visibleVideos, playlistStore.currentId()) : selectedPoster(context);
            if (poster != null) { files.clear(); files.add(poster); }
        }
        boolean imageMode = !files.isEmpty() && (config.wallpaperEnabled || playlistMode
                || "video".equals(settings.getString("background_mode", "image")));
        if (imageMode) {
            backgroundRoot.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
        videoMode = playlistMode || selectedVideo(context) != null;
        if (playlistMode) {
            playlistController = new WallpaperPlaylistController(context, this, visibleVideos,
                    settings.getString(Config.WALLPAPER_CHOICE_KEY, ""));
            videoPlayer = null;
            if (playlistController.usable()) playlistController.showInitialPoster();
        } else {
            playlistController = null;
            File video = selectedVideo(context);
            videoPlayer = videoMode ? addVideo(context, root, video) : null;
        }
        // No shade layer at all when the theme doesn't dim: a full-screen view is overdraw on 1 GB TVs.
        if (config.wallpaperDim > 0 && (imageMode || videoMode)) {
            View shade = new View(context);
            shade.setBackgroundColor((config.wallpaperDim * 255 / 100) << 24);
            shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            backgroundRoot.addView(shade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    /**
     * The picture Home opens with is decoded right here, on the main thread, as the single wallpaper always was:
     * Home's first frame already has it instead of drawing the plain colour and the picture popping in later.
     * Only the later changes of a rotation decode in the background.
     */
    private void showFirst() {
        String previous = preferences.getString("image", "");
        File first = WallpaperRotation.find(files, previous);
        if (first == null) first = WallpaperRotation.pick(files, previous, random);
        while (first != null) {
            Bitmap bitmap = decode(first);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
                displayed = first.getName();
                if (!displayed.equals(previous)) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
                return;
            }
            files.remove(first);   // unreadable or over 4K: try another, or keep the plain colour
            first = WallpaperRotation.pick(files, previous, random);
        }
    }

    void start() {
        if (closed) return;
        active = true;
        if (dissolve != null) dissolve.resume();
        if (playlistController != null) playlistController.start();
        else if (videoPlayer != null) videoPlayer.start();
        update();
    }

    void stop() {
        active = false;
        if (dissolve != null) dissolve.pause();
        if (playlistController != null) playlistController.stop();
        else if (videoPlayer != null) videoPlayer.stop();
    }

    void close() {
        active = false;
        closed = true;
        if (playlistController != null) playlistController.close();
        else if (videoPlayer != null) videoPlayer.close();
        if (dissolve != null) dissolve.close();
        visibleSession = null;
        playlistViews.clear();
        decoder.shutdownNow();
    }

    Context context() { return context; }
    String activeChoice() {
        return playlistController == null ? "" : playlistController.currentId();
    }
    String activePath() {
        return playlistController == null ? "" : playlistController.currentPath(context);
    }

    @Override public WallpaperVideoSession createPlaylistVideo(WallpaperCandidate candidate, boolean looping,
                                                               Runnable completed, Runnable failed) {
        File file = WallpaperCatalog.resolve(Config.dir(context), candidate.relativeFile);
        if (file == null || !file.isFile()) return null;
        WallpaperPlaylistStore.seedFixedBookmark(context, candidate.id, file);
        WallpaperVideoSession session = addVideo(context, file, candidate.id, looping, completed, failed);
        visibleSession = session;
        if (dissolve != null) {
            dissolve.bind(session, candidate.id);
            session.setVisualEvents(new WallpaperVideoSession.VisualEvents() {
                @Override public void approachingEnd() {
                    if (!closed && active && visibleSession == session) dissolve.fadeOut(session);
                }
                @Override public void firstFrame() {
                    if (closed || !active || visibleSession != session) return;
                    ++playlistPosterGeneration; // A late poster must never cover the playing video.
                    image.setVisibility(View.GONE);
                    new WallpaperPlaylistStore(context).markFirstFrame(android.os.SystemClock.elapsedRealtime());
                    dissolve.firstFrame(session);
                }
            });
        }
        return session;
    }

    @Override public void showPlaylistPoster(WallpaperCandidate candidate) {
        if (dissolve != null && dissolve.holding()) return;
        File poster = WallpaperCatalog.resolve(Config.dir(context), candidate.posterRelativeFile);
        if (poster == null || !poster.isFile()) return;
        final int token = ++playlistPosterGeneration;
        decoder.execute(() -> {
            Bitmap bitmap = decode(poster);
            backgroundRoot.post(() -> {
                if (closed || token != playlistPosterGeneration || bitmap == null
                        || (dissolve != null && dissolve.holding())) {
                    if (bitmap != null) bitmap.recycle(); return;
                }
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
            });
        });
    }

    @Override public void removePlaylistVideo(WallpaperVideoSession session) {
        List<View> views = playlistViews.remove(session);
        if (views != null) for (View view : views) backgroundRoot.removeView(view);
    }

    @Override public void preparePlaylistTransition(WallpaperVideoSession old, Runnable readyToRelease) {
        ++playlistPosterGeneration;
        if (dissolve == null) readyToRelease.run();
        else {
            WallpaperPlaylistStore store = new WallpaperPlaylistStore(context);
            dissolve.hold(old, store.advanceCount(), store.currentId(), store.lastReason(),
                    store.completedAt(), readyToRelease);
        }
    }

    @Override public void onPlaylistSuspended() {
        if (dissolve != null) dissolve.abort();
        image.setVisibility(View.VISIBLE);
    }

    @Override public void onPlaylistBindingChanged() {
        if (playlistController == null) return;
        MainActivity.activeWallpaperChoice = playlistController.currentId();
        MainActivity.activeWallpaperPath = playlistController.currentPath(context);
    }

    void update() {
        if (videoMode || !active || closed || loading || files.isEmpty()) return;
        String previous = preferences.getString("image", "");
        File selected = WallpaperRotation.find(files, previous);
        long changed = preferences.getLong("changed", 0);
        if (selected == null || WallpaperRotation.due(changed, System.currentTimeMillis(), interval)) {
            selected = WallpaperRotation.pick(files, previous, random);
        }
        if (selected == null || selected.getName().equals(displayed)) return;
        File next = selected;
        loading = true;
        decoder.execute(() -> {
            Bitmap bitmap = decode(next);
            backgroundRoot.post(() -> {
                loading = false;
                if (closed || !active) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                if (bitmap == null) {
                    files.remove(next);
                    Log.w("TvHome.Wallpaper", "Skipping unreadable image: " + next.getName());
                    update();
                    return;
                }
                image.setImageBitmap(bitmap);
                displayed = next.getName();
                if (!displayed.equals(previous) || changed == 0) {
                    preferences.edit().putString("image", displayed)
                            .putLong("changed", System.currentTimeMillis()).apply();
                }
            });
        });
    }

    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, this.width, this.height);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, this.width, this.height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private File selectedVideo(Context context) {
        if (settings.getBoolean("static_cover", false)) return null;
        if (!"video".equals(settings.getString("background_mode", "image"))) return null;
        String path = settings.getString("video_wallpaper_path", "");
        if (path == null || path.isEmpty()) return null;
        File file = new File(path);
        return file.isFile() && VideoWallpaperPolicy.safeSourceName(file.getName()) ? file : null;
    }

    private File selectedPoster(Context context) {
        String choice = settings.getString(Config.WALLPAPER_CHOICE_KEY, "");
        String video = settings.getString("video_wallpaper_path", "");
        for (WallpaperCandidate candidate : WallpaperCatalog.load(Config.dir(context))) {
            File source = WallpaperCatalog.resolve(Config.dir(context), candidate.relativeFile);
            if (candidate.isDynamic() && (candidate.id.equals(choice)
                    || (source != null && source.getAbsolutePath().equals(video)))) {
                File poster = WallpaperCatalog.resolve(Config.dir(context), candidate.posterRelativeFile);
                if (poster != null && poster.isFile()) return poster;
            }
        }
        return null;
    }

    private File posterFor(Context context, List<WallpaperCandidate> candidates, String id) {
        for (WallpaperCandidate candidate : candidates) {
            if (candidate.id.equals(id)) {
                File poster = WallpaperCatalog.resolve(Config.dir(context), candidate.posterRelativeFile);
                if (poster != null && poster.isFile()) return poster;
            }
        }
        return candidates.isEmpty() ? null : WallpaperCatalog.resolve(Config.dir(context), candidates.get(0).posterRelativeFile);
    }

    private WallpaperVideoSession addVideo(Context context, FrameLayout root, File file) {
        return addVideo(context, file, "", true, null, null);
    }

    private WallpaperVideoSession addVideo(Context context, File file, String playlistId,
                                           boolean looping, Runnable completed, Runnable failed) {
        for (WallpaperCandidate candidate : WallpaperCatalog.load(Config.dir(context))) {
            File source = WallpaperCatalog.resolve(Config.dir(context), candidate.relativeFile);
            if (source != null && source.equals(file) && VideoWallpaperPolicy.needsNativeSurface(
                    candidate.width, candidate.height, candidate.fps, file.length())) {
                android.view.SurfaceView surface = new android.view.SurfaceView(context);
                surface.setContentDescription("TVHome UHD wallpaper surface");
                surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                backgroundRoot.addView(surface, 0, new FrameLayout.LayoutParams(-1, -1));
                // Cover is above the SurfaceView hole, below the Dock and all other UI.
                if (image.getParent() == null) backgroundRoot.addView(image, 1,
                        new FrameLayout.LayoutParams(-1, -1));
                NativeVideoWallpaperPlayer player = new NativeVideoWallpaperPlayer(context, surface, image, file,
                        looping, completed, playlistId.isEmpty() ? null : WallpaperPlaylistStore.bookmark(context, playlistId, file),
                        failed);
                if (!playlistId.isEmpty()) playlistViews.put(player, java.util.Collections.<View>singletonList(surface));
                return player;
            }
        }
        TextureView texture = new TextureView(context);
        texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int insertAt = dissolveOverlay == null ? backgroundRoot.getChildCount()
                : backgroundRoot.indexOfChild(dissolveOverlay);
        backgroundRoot.addView(texture, insertAt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        VideoWallpaperPlayer.Failure onFailure = failed == null
                ? () -> texture.setVisibility(View.GONE)
                : () -> failed.run();
        VideoWallpaperPlayer player = new VideoWallpaperPlayer(context, texture, file,
                BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER,
                onFailure,
                looping, completed, playlistId.isEmpty() ? null : WallpaperPlaylistStore.bookmark(context, playlistId, file));
        if (!playlistId.isEmpty()) {
            List<View> views = new ArrayList<>();
            views.add(texture);
            int index = backgroundRoot.indexOfChild(texture) + 1;
            while (index < backgroundRoot.getChildCount()) {
                View child = backgroundRoot.getChildAt(index);
                if (child instanceof ImageView && child != image && child != dissolveOverlay) { views.add(child); index++; }
                else break;
            }
            playlistViews.put(player, views);
        }
        return player;
    }
}
