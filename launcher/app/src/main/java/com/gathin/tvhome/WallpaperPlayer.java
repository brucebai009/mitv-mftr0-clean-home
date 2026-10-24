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
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Displays and rotates a theme's local wallpaper images while Home is visible. */
final class WallpaperPlayer {
    private final FrameLayout root;
    /** Pure background container; UI, focus and dialogs stay above this layer. */
    private final FrameLayout backgroundRoot;
    private final ImageView image;
    private final List<File> files;
    private final long interval;
    private final SharedPreferences preferences;
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private final int width;
    private final int height;
    private final SharedPreferences settings;
    private final VideoWallpaperPlayer videoPlayer;
    private final boolean videoMode;
    private boolean active;
    private boolean closed;
    private boolean loading;
    private String displayed = "";

    WallpaperPlayer(Context context, FrameLayout root, Config config) {
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
        if ("video".equals(settings.getString("background_mode", "image"))) {
            File poster = selectedPoster(context);
            if (poster != null) { files.clear(); files.add(poster); }
        }
        boolean imageMode = !files.isEmpty() && (config.wallpaperEnabled
                || "video".equals(settings.getString("background_mode", "image")));
        if (imageMode) {
            backgroundRoot.addView(image, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showFirst();
        }
        File video = selectedVideo(context);
        videoMode = video != null;
        videoPlayer = videoMode ? addVideo(context, root, video) : null;
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
        if (videoPlayer != null) videoPlayer.start();
        update();
    }

    void stop() {
        active = false;
        if (videoPlayer != null) videoPlayer.stop();
    }

    void close() {
        active = false;
        closed = true;
        if (videoPlayer != null) videoPlayer.close();
        decoder.shutdownNow();
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

    private VideoWallpaperPlayer addVideo(Context context, FrameLayout root, File file) {
        TextureView texture = new TextureView(context);
        texture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backgroundRoot.addView(texture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new VideoWallpaperPlayer(context, texture, file, () -> texture.setVisibility(View.GONE));
    }
}
