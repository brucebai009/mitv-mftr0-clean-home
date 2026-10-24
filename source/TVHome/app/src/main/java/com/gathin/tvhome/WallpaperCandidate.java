package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One user-visible wallpaper entry. Paths are relative to Config.dir and never exposed as labels. */
final class WallpaperCandidate {
    enum Type { IMAGE, VIDEO, DEFAULT }

    final String id;
    final String title;
    final Type type;
    final String relativeFile;
    final String posterRelativeFile;
    final String sha256;
    final int width;
    final int height;
    final int fps;
    final long durationMs;
    final String author;
    final List<String> variants;

    WallpaperCandidate(String id, String title, Type type, String relativeFile,
                       String posterRelativeFile, String sha256, int width, int height,
                       int fps, long durationMs, String author, List<String> variants) {
        this.id = id == null ? "" : id;
        this.title = title == null || title.trim().isEmpty() ? "壁纸" : title.trim();
        this.type = type == null ? Type.IMAGE : type;
        this.relativeFile = relativeFile == null ? "" : relativeFile;
        this.posterRelativeFile = posterRelativeFile == null ? "" : posterRelativeFile;
        this.sha256 = sha256 == null ? "" : sha256;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.fps = Math.max(0, fps);
        this.durationMs = Math.max(0L, durationMs);
        this.author = author == null ? "" : author;
        this.variants = Collections.unmodifiableList(new ArrayList<>(
                variants == null ? Collections.emptyList() : variants));
    }

    static WallpaperCandidate defaultBackground() {
        return new WallpaperCandidate("default", "默认背景", Type.DEFAULT,
                "", "", "", 0, 0, 0, 0, "", Collections.emptyList());
    }

    boolean isDynamic() { return type == Type.VIDEO; }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("type", type == Type.VIDEO ? "video" : type == Type.DEFAULT ? "default" : "image");
            json.put("relativeFile", relativeFile);
            json.put("posterRelativeFile", posterRelativeFile);
            json.put("sha256", sha256);
            json.put("width", width);
            json.put("height", height);
            json.put("fps", fps);
            json.put("durationMs", durationMs);
            json.put("author", author);
            JSONArray values = new JSONArray();
            for (String variant : variants) values.put(variant);
            json.put("variants", values);
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException("Could not encode wallpaper candidate", impossible);
        }
        return json;
    }

    static WallpaperCandidate fromJson(JSONObject json) {
        String type = json.optString("type", "image");
        Type parsed = "video".equalsIgnoreCase(type) ? Type.VIDEO
                : "default".equalsIgnoreCase(type) ? Type.DEFAULT : Type.IMAGE;
        List<String> variants = new ArrayList<>();
        JSONArray values = json.optJSONArray("variants");
        if (values != null) for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "").trim();
            if (!value.isEmpty()) variants.add(value);
        }
        return new WallpaperCandidate(json.optString("id", ""), json.optString("title", "壁纸"),
                parsed, json.optString("relativeFile", ""), json.optString("posterRelativeFile", ""),
                json.optString("sha256", ""), json.optInt("width", 0), json.optInt("height", 0),
                json.optInt("fps", 0), json.optLong("durationMs", 0L), json.optString("author", ""), variants);
    }
}
