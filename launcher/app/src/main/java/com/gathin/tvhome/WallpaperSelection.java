package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Objects;

/** The effective player input, independent of Activity result delivery and the process lifetime. */
final class WallpaperSelection {
    final String mode, choice, path;
    final boolean staticCover;
    final int dim;
    final boolean playlistEnabled;
    final String playlistIds;

    WallpaperSelection(String mode, String choice, String path, boolean staticCover, int dim) {
        this(mode, choice, path, staticCover, dim, false, "[]");
    }

    WallpaperSelection(String mode, String choice, String path, boolean staticCover, int dim,
                       boolean playlistEnabled, String playlistIds) {
        this.mode = mode; this.choice = choice; this.path = path;
        this.staticCover = staticCover; this.dim = dim;
        this.playlistEnabled = playlistEnabled;
        this.playlistIds = playlistIds == null ? "[]" : playlistIds;
    }

    static WallpaperSelection read(Context context) {
        SharedPreferences p = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return new WallpaperSelection(p.getString("background_mode", "image"),
                p.getString(Config.WALLPAPER_CHOICE_KEY, ""), p.getString("video_wallpaper_path", ""),
                p.getBoolean("static_cover", false), p.getInt(Config.WALLPAPER_DIM_KEY, -1),
                p.getBoolean(WallpaperPlaylistStore.ENABLED_KEY, false),
                p.getString(WallpaperPlaylistStore.IDS_KEY, "[]"));
    }

    @Override public boolean equals(Object value) {
        if (!(value instanceof WallpaperSelection)) return false;
        WallpaperSelection other = (WallpaperSelection) value;
        return Objects.equals(mode, other.mode) && Objects.equals(choice, other.choice)
                && Objects.equals(path, other.path) && staticCover == other.staticCover && dim == other.dim
                && playlistEnabled == other.playlistEnabled && Objects.equals(playlistIds, other.playlistIds);
    }

    @Override public int hashCode() { return Objects.hash(mode, choice, path, staticCover, dim, playlistEnabled, playlistIds); }
}
