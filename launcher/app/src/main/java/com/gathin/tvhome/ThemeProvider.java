package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.gathin.tvhome.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.gathin.tvhome.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.gathin.tvhome.theme/config.json
 *   content delete --uri content://com.gathin.tvhome.theme/wallpapers      (every image in the collection)
 */
public class ThemeProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File dir = Config.dir(getContext());
        if (dir == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(dir, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File dir = Config.dir(getContext());
        if (dir != null && ThemeFiles.isCollection(uri.getPathSegments())) {
            String collection = ThemeFiles.WALLPAPERS.equals(uri.getPathSegments().get(0))
                    ? ThemeFiles.WALLPAPERS : ThemeFiles.VIDEOS;
            File[] images = new File(dir, collection).listFiles((d, name) ->
                    ThemeFiles.WALLPAPERS.equals(collection) ? ThemeFiles.collectionImage(name) : ThemeFiles.collectionVideo(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }


    /** Shell-only diagnostic/selection bridge. No HOME, package-manager or network mutations. */
    @Override public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        if (getContext() == null || getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SECURE_SETTINGS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("WRITE_SECURE_SETTINGS required");
        if (!"video_probe".equals(method) && !"select_wallpaper".equals(method) && !"wallpaper_status".equals(method) && !"commit_wallpaper".equals(method) && !"remove_wallpapers".equals(method))
            throw new IllegalArgumentException("Unsupported wallpaper operation");
        try {
            android.os.Bundle result = new android.os.Bundle();
            org.json.JSONObject json = new org.json.JSONObject();
            android.content.SharedPreferences settings = getContext().getSharedPreferences(Config.SETTINGS_NAME, android.content.Context.MODE_PRIVATE);
            json.put("selected", settings.getString(Config.WALLPAPER_CHOICE_KEY, ""));
            json.put("fixedWallpaperChoice", settings.getString(Config.WALLPAPER_CHOICE_KEY, ""));
            json.put("background_mode", settings.getString("background_mode", "image"));
            json.put("staticCover", settings.getBoolean("static_cover", false));
            WallpaperPlaylistStore playlist = new WallpaperPlaylistStore(getContext());
            json.put("playlistEnabled", playlist.enabled());
            json.put("playlistIds", new org.json.JSONArray(playlist.ids()));
            json.put("playlistCurrentId", playlist.currentId());
            json.put("playlistIndex", WallpaperPlaylistPolicy.indexOf(playlist.ids(), playlist.currentId()));
            json.put("playlistState", playlist.state());
            json.put("playlistGeneration", playlist.generation());
            json.put("playlistAdvanceCount", playlist.advanceCount());
            json.put("playlistLastReason", playlist.lastReason());
            json.put("playlistFailedIds", playlist.failedIds());
            json.put("playlistCompletedAtElapsedMs", playlist.completedAt());
            json.put("playlistFirstFrameAtElapsedMs", playlist.firstFrameAt());
            json.put("fadeStage", WallpaperDissolve.lastStage);
            json.put("fadeBlackHoldMs", WallpaperDissolve.lastHoldMs);
            json.put("fadeInMs", WallpaperDissolve.lastFadeMs);
            json.put("fadeTransitions", TransitionJournal.snapshot());
            json.put("modalBackdropLayers", FrostedBackdrop.activeLayers);
            json.put("modalBackdropResult", FrostedBackdrop.lastResult);
            json.put("modalBackdropCaptures", FrostedBackdrop.completedCaptures);
            json.put("modalBackdropBlurMs", FrostedBackdrop.lastBlurMs);
            json.put("activeWallpaperPath", MainActivity.activeWallpaperPath);
            json.put("activeWallpaperChoice", MainActivity.activeWallpaperChoice);
            json.put("activeStaticCover", MainActivity.activeStaticCover);
            json.put("wallpaperBindCount", MainActivity.wallpaperBindCount);
            json.put("wallpaperIdle", WallpaperIdleController.lastIdle);
            json.put("wallpaperIdleEntry", WallpaperIdleController.lastEntryReason);
            json.put("wallpaperIdleMinutes", WallpaperIdlePolicy.normalizeMinutes(settings.getInt(WallpaperIdlePolicy.KEY, 4)));
            json.put("gridPackages", new org.json.JSONArray(AppAdapter.lastGridPackages));
            json.put("dockPackages", new org.json.JSONArray(DockPreferences.load(getContext()) == null
                    ? java.util.Collections.emptyList() : DockPreferences.load(getContext())));
            json.put("dockVisiblePackages", new org.json.JSONArray(AppAdapter.lastDockPackages));
            json.put("homeScreenGuard", WallpaperIdleController.lastScreenGuard);
            json.put("dockTopPx", MainActivity.dockTopPx);
            json.put("dockTargetTopPx", MainActivity.dockTargetTopPx);
            json.put("homeFocusedPosition", MainActivity.focusedPosition);
            json.put("dockTransition", DockScrollTransition.lastResult);
            json.put("dockTransitionMs", DockScrollTransition.lastDurationMs);
            json.put("dockFocusAtMs", DockScrollTransition.lastFocusAtMs);
            json.put("dockAnimationEndMs", DockScrollTransition.lastAnimationEndMs);
            json.put("dockFocusDelayMs", DockScrollTransition.lastFocusDelayMs);
            json.put("dockAnchorClamped", DockScrollTransition.lastAnchorClamped);
            json.put("decodeProbe", new org.json.JSONObject(VideoCompatibilityProbeActivity.lastReport));
            org.json.JSONArray visible = new org.json.JSONArray();
            for (WallpaperCandidate item : WallpaperCatalog.visibleVideos(Config.dir(getContext()))) visible.put(item.toJson());
            json.put("visibleWallpapers", visible);
            android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
            ((android.app.ActivityManager)getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
            json.put("totalMem", memory.totalMem); json.put("availMem", memory.availMem); json.put("lowMemory", memory.lowMemory);
            json.put("storageAvailable", Config.dir(getContext()).getUsableSpace());
            android.view.Display display = ((android.hardware.display.DisplayManager)getContext().getSystemService(android.content.Context.DISPLAY_SERVICE)).getDisplay(0);
            if (display != null) {
                android.view.Display.Mode mode = display.getMode();
                json.put("androidDisplayWidth", mode.getPhysicalWidth()); json.put("androidDisplayHeight", mode.getPhysicalHeight());
                json.put("androidRefreshRate", mode.getRefreshRate());
                json.put("displayNote", "Android mode may describe the upscaled UI; physical panel requires SurfaceFlinger/vendor evidence");
            }
            if ("remove_wallpapers".equals(method)) {
                if (!"video".equals(settings.getString("background_mode", ""))
                        || !settings.getString("video_wallpaper_path", "").equals(MainActivity.activeWallpaperPath)
                        || MainActivity.activeWallpaperPath.isEmpty())
                    throw new IllegalStateException("Resume a verified retained video before removal");
                java.util.Set<String> ids = new java.util.LinkedHashSet<>();
                boolean images = false;
                for (String id : (arg == null ? "" : arg).split(",")) {
                    if ("images".equals(id)) images = true;
                    else if (id.matches("[A-Za-z0-9_.-]+")) ids.add(id);
                    else throw new IllegalArgumentException("Exact ids required");
                }
                json.put("cleanup", WallpaperCatalog.remove(Config.dir(getContext()), images, ids,
                        settings.getString(Config.WALLPAPER_CHOICE_KEY, ""),
                        settings.getString("video_wallpaper_path", ""), MainActivity.activeWallpaperPath));
            } else if ("commit_wallpaper".equals(method)) {
                File metadata = new File(Config.dir(getContext()), "wallpaper_import.json");
                if (!metadata.isFile() || metadata.length() > 1024 * 1024) throw new java.io.IOException("Import metadata unavailable");
                WallpaperCandidate candidate = WallpaperCandidate.fromJson(new org.json.JSONObject(new String(
                        java.nio.file.Files.readAllBytes(metadata.toPath()), java.nio.charset.StandardCharsets.UTF_8)));
                File media = WallpaperCatalog.resolve(Config.dir(getContext()), candidate.relativeFile);
                if (!candidate.isDynamic() || media == null || !media.isFile() || candidate.sha256.isEmpty()
                        || !candidate.sha256.equalsIgnoreCase(VideoWallpaperImport.sha256(media)))
                    throw new java.io.IOException("Import hash/path verification failed");
                VideoSourceInfo info = VideoSourceInfo.inspect(media);
                if (VideoWallpaperPolicy.validate(media.getName(), media.length(), info.width, info.height,
                        info.durationMs, info.fps, info.mime, info.sdr) != VideoWallpaperPolicy.Result.VALID)
                    throw new java.io.IOException("Unsupported original video specifications");
                WallpaperCandidate actual = new WallpaperCandidate(candidate.id, candidate.title, candidate.type,
                        candidate.relativeFile, candidate.posterRelativeFile, candidate.sha256,
                        info.width, info.height, info.fps, info.durationMs, candidate.author, candidate.variants);
                WallpaperCatalog.upsert(Config.dir(getContext()), actual);
                json.put("imported", actual.id); json.put("video", info.diagnosticJson());
                json.put("playbackStatus", "IMPORTED_BYTES_ONLY_REQUIRES_PLAYBACK_VERIFICATION");
                metadata.delete();
            } else if (!"wallpaper_status".equals(method)) {
                WallpaperCandidate selected = null;
                for (WallpaperCandidate candidate : WallpaperCatalog.load(Config.dir(getContext())))
                    if (candidate.id.equals(arg)) { selected = candidate; break; }
                if (selected == null) throw new IllegalArgumentException("Unknown wallpaper id");
                File source = WallpaperCatalog.resolve(Config.dir(getContext()), selected.relativeFile);
                if (selected.isDynamic()) {
                    VideoSourceInfo info = VideoSourceInfo.inspect(source);
                    json.put("video", info.diagnosticJson());
                    json.put("verifiedHardwareDecode", HardwarePlaybackProof.has(getContext(), source));
                    json.put("policy", VideoWallpaperPolicy.validate(source.getName(), source.length(), info.width, info.height,
                            info.durationMs, info.fps, info.mime, info.sdr).name());
                    if ("select_wallpaper".equals(method)) {
                        if (!"VALID".equals(json.getString("policy"))) throw new IllegalArgumentException("Unsupported original video");
                        if (info.needsNativeSurface(source.length()) && !info.canPlayOriginal(getContext(), source)) throw new IllegalArgumentException("No hardware decoder for original media");
                        if (!selected.sha256.isEmpty() && !selected.sha256.equalsIgnoreCase(VideoWallpaperImport.sha256(source)))
                            throw new java.io.IOException("Video hash mismatch");
                    }
                }
                if ("select_wallpaper".equals(method)) {
                    android.content.SharedPreferences.Editor editor = settings.edit()
                            .putBoolean(WallpaperPlaylistStore.ENABLED_KEY, false)
                            .putBoolean("static_cover", false)
                            .putString("background_mode", selected.isDynamic() ? "video" : "image");
                    if (selected.type == WallpaperCandidate.Type.DEFAULT) editor.remove(Config.WALLPAPER_CHOICE_KEY).remove("video_wallpaper_path");
                    else if (selected.isDynamic()) editor.putString(Config.WALLPAPER_CHOICE_KEY, selected.id).putString("video_wallpaper_path", source.getAbsolutePath());
                    else editor.putString(Config.WALLPAPER_CHOICE_KEY, source.getName()).remove("video_wallpaper_path");
                    if (!editor.commit()) throw new java.io.IOException("Could not save selection");
                    json.put("selected", selected.id); json.put("restartHomeRequired", false);
                    json.put("applyOnHomeResume", true);
                }
            }
            result.putString("json", json.toString()); return result;
        } catch (Exception error) { throw new IllegalArgumentException("Wallpaper operation failed: " + error.getMessage(), error); }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
