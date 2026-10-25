package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Additive, ordered playlist state. Fixed-wallpaper keys remain untouched. */
final class WallpaperPlaylistStore {
    static final String ENABLED_KEY = "wallpaper_playlist_enabled";
    static final String IDS_KEY = "wallpaper_playlist_ids";
    static final String CURRENT_KEY = "wallpaper_playlist_current_id";
    static final String PENDING_KEY = "wallpaper_playlist_pending_next";
    static final String STATE_KEY = "wallpaper_playlist_state";
    static final String GENERATION_KEY = "wallpaper_playlist_generation";
    static final String ADVANCE_COUNT_KEY = "wallpaper_playlist_advance_count";
    static final String LAST_REASON_KEY = "wallpaper_playlist_last_reason";
    static final String FAILED_IDS_KEY = "wallpaper_playlist_failed_ids";
    static final String COMPLETED_AT_KEY = "wallpaper_playlist_completed_at";
    static final String FIRST_FRAME_AT_KEY = "wallpaper_playlist_first_frame_at";

    private final SharedPreferences preferences;

    WallpaperPlaylistStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    boolean enabled() { return preferences.getBoolean(ENABLED_KEY, false); }
    String currentId() { return preferences.getString(CURRENT_KEY, ""); }
    String pendingNext() { return preferences.getString(PENDING_KEY, ""); }
    String state() { return preferences.getString(STATE_KEY, enabled() ? "READY" : "OFF"); }
    long generation() { return preferences.getLong(GENERATION_KEY, 0L); }
    long advanceCount() { return preferences.getLong(ADVANCE_COUNT_KEY, 0L); }
    String lastReason() { return preferences.getString(LAST_REASON_KEY, ""); }
    String failedIds() { return preferences.getString(FAILED_IDS_KEY, ""); }
    long completedAt() { return preferences.getLong(COMPLETED_AT_KEY, 0L); }
    long firstFrameAt() { return preferences.getLong(FIRST_FRAME_AT_KEY, 0L); }

    List<String> ids() {
        String raw = preferences.getString(IDS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, "");
                if (!id.isEmpty() && !result.contains(id)) result.add(id);
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    void saveIds(List<String> ids) {
        preferences.edit().putString(IDS_KEY, encode(ids)).commit();
    }

    void setEnabled(boolean enabled, String currentId) {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(ENABLED_KEY, enabled)
                .putString(STATE_KEY, enabled ? "READY" : "OFF")
                .putString(LAST_REASON_KEY, enabled ? "USER_ENABLED" : "USER_DISABLED")
                .putLong(GENERATION_KEY, generation() + 1L);
        if (currentId != null) editor.putString(CURRENT_KEY, currentId);
        if (!enabled) editor.remove(PENDING_KEY);
        if (!editor.commit()) throw new IllegalStateException("Could not save playlist state");
    }

    void saveSelection(List<String> ids) {
        if (!preferences.edit().putString(IDS_KEY, encode(ids)).commit())
            throw new IllegalStateException("Could not save playlist selection");
    }

    void enableWithSelection(List<String> ids, String currentId) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(IDS_KEY, encode(ids)).putBoolean(ENABLED_KEY, true)
                .putString(CURRENT_KEY, currentId == null ? "" : currentId)
                .putString(STATE_KEY, "READY").putString(LAST_REASON_KEY, "USER_ENABLED")
                .putLong(GENERATION_KEY, generation() + 1L);
        if (!editor.commit()) throw new IllegalStateException("Could not enable wallpaper playlist");
    }

    void setCurrent(String id, String reason) {
        preferences.edit().putString(CURRENT_KEY, id == null ? "" : id)
                .remove(PENDING_KEY).putString(STATE_KEY, "PLAYING")
                .putString(LAST_REASON_KEY, reason == null ? "" : reason).commit();
    }

    void markPending(String id) {
        preferences.edit().putString(PENDING_KEY, id == null ? "" : id)
                .putString(STATE_KEY, "NEXT_PENDING").commit();
    }

    void markAdvance(String currentId, String reason, long now) {
        preferences.edit().putString(CURRENT_KEY, currentId == null ? "" : currentId)
                .putString(STATE_KEY, "NEXT_PENDING")
                .putString(LAST_REASON_KEY, reason == null ? "" : reason)
                .putLong(GENERATION_KEY, generation() + 1L)
                .putLong(ADVANCE_COUNT_KEY, advanceCount() + 1L)
                .putLong(COMPLETED_AT_KEY, now).commit();
    }

    void markFirstFrame(long now) { preferences.edit().putLong(FIRST_FRAME_AT_KEY, now).apply(); }

    void markSuspended(String failed) {
        preferences.edit().putString(STATE_KEY, "ERROR_SUSPENDED")
                .putString(FAILED_IDS_KEY, failed == null ? "" : failed)
                .putString(LAST_REASON_KEY, "ALL_ITEMS_UNUSABLE").commit();
    }

    static String encode(List<String> ids) {
        JSONArray array = new JSONArray();
        if (ids != null) for (String id : ids) if (id != null && !id.isEmpty()) array.put(id);
        return array.toString();
    }

    static String sourceKey(File file) {
        return file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
    }

    static PlaybackBookmark bookmark(Context context, String id, File file) {
        SharedPreferences preferences = context.getApplicationContext().getSharedPreferences(
                "wallpaper_playlist_bookmarks", Context.MODE_PRIVATE);
        String prefix = "bookmark." + id + ".";
        String sourceKey = sourceKey(file);
        return new PlaybackBookmark(sourceKey, new PlaybackBookmark.Store() {
            @Override public String source() { return preferences.getString(prefix + "source", ""); }
            @Override public long position() { return preferences.getLong(prefix + "position", 0L); }
            @Override public void save(String source, long position) {
                preferences.edit().putString(prefix + "source", source)
                        .putLong(prefix + "position", position).apply();
            }
        });
    }

    static void clearBookmark(Context context, String id) {
        context.getApplicationContext().getSharedPreferences("wallpaper_playlist_bookmarks", Context.MODE_PRIVATE)
                .edit().remove("bookmark." + id + ".source")
                .remove("bookmark." + id + ".position").apply();
    }

    static void seedFixedBookmark(Context context, String id, File file) {
        SharedPreferences fixed = context.getApplicationContext().getSharedPreferences(
                "video_resume_VIDEO_WALLPAPER", Context.MODE_PRIVATE);
        String source = fixed.getString("source", "");
        long position = fixed.getLong("position", 0L);
        if (!source.equals(sourceKey(file)) || position <= 0L) return;
        SharedPreferences playlist = context.getApplicationContext().getSharedPreferences(
                "wallpaper_playlist_bookmarks", Context.MODE_PRIVATE);
        String prefix = "bookmark." + id + ".";
        if (!playlist.contains(prefix + "source")) playlist.edit().putString(prefix + "source", source)
                .putLong(prefix + "position", position).apply();
    }
}
