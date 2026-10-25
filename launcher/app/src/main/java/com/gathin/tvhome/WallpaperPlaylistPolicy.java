package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering rules for the video wallpaper playlist. No timing or player state lives here. */
final class WallpaperPlaylistPolicy {
    private WallpaperPlaylistPolicy() { }

    static List<String> normalizeSelection(List<WallpaperCandidate> visible, List<String> saved) {
        Set<String> visibleIds = new HashSet<>();
        for (WallpaperCandidate candidate : visible) {
            if (candidate != null && candidate.isDynamic() && candidate.id != null && !candidate.id.isEmpty()) {
                visibleIds.add(candidate.id);
            }
        }
        List<String> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        if (saved != null) {
            for (String id : saved) {
                if (id != null && visibleIds.contains(id) && added.add(id)) result.add(id);
            }
        }
        return result;
    }

    static List<String> initialSelection(List<WallpaperCandidate> visible) {
        List<String> result = new ArrayList<>();
        if (visible == null) return result;
        for (WallpaperCandidate candidate : visible) {
            if (candidate != null && candidate.isDynamic() && candidate.id != null
                    && !candidate.id.isEmpty()) result.add(candidate.id);
        }
        return result;
    }

    static List<String> reconcileSelection(List<WallpaperCandidate> visible, List<String> saved,
                                           List<String> newlySelected) {
        List<String> existing = normalizeSelection(visible, saved);
        List<String> displayOrder = initialSelection(visible);
        Set<String> selected = new HashSet<>();
        if (newlySelected != null) selected.addAll(newlySelected);
        List<String> result = new ArrayList<>();
        for (String id : existing) if (selected.contains(id)) result.add(id);
        for (String id : displayOrder) if (selected.contains(id) && !result.contains(id)) result.add(id);
        return result;
    }

    static int indexOf(List<String> ids, String id) {
        if (ids == null || id == null) return -1;
        for (int i = 0; i < ids.size(); i++) if (id.equals(ids.get(i))) return i;
        return -1;
    }

    static String nextId(List<String> ids, String currentId) {
        if (ids == null || ids.isEmpty()) return "";
        int index = indexOf(ids, currentId);
        return ids.get(index < 0 ? 0 : (index + 1) % ids.size());
    }

    static boolean canEnable(List<String> ids) { return ids != null && !ids.isEmpty(); }

    static boolean usesNaturalCompletion(List<String> ids) { return ids != null && ids.size() >= 2; }
}
