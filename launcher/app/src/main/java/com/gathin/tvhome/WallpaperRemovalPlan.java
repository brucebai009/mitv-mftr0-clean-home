package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Exact catalog references, not filename substrings; shared video posters are never image cleanup targets. */
final class WallpaperRemovalPlan {
    final List<WallpaperCandidate> keep = new ArrayList<>();
    final Set<String> paths = new LinkedHashSet<>();
    final Set<String> removedIds = new LinkedHashSet<>();
    final Set<String> protectedPosters = new LinkedHashSet<>();

    static WallpaperRemovalPlan create(List<WallpaperCandidate> entries, boolean images, Set<String> ids,
                                       String selectedId, String selectedPath, String activePath) {
        WallpaperRemovalPlan plan = new WallpaperRemovalPlan();
        for (WallpaperCandidate item : entries) {
            if (item.type == WallpaperCandidate.Type.DEFAULT) { plan.keep.add(item); continue; }
            if (images && item.type == WallpaperCandidate.Type.IMAGE || ids.contains(item.id)) {
                // The running old player can still own a previously selected source until Home resumes.
                if (item.id.equals(selectedId) || item.relativeFile.equals(selectedPath)
                        || item.relativeFile.equals(activePath))
                    throw new IllegalStateException("Select a verified retained video and resume Home before cleanup");
                plan.removedIds.add(item.id);
                plan.paths.add(item.relativeFile);
                if (!item.posterRelativeFile.isEmpty()) plan.paths.add(item.posterRelativeFile);
            } else plan.keep.add(item);
        }
        for (WallpaperCandidate item : plan.keep) {
            plan.paths.remove(item.relativeFile);
            if (!item.posterRelativeFile.isEmpty()) plan.protectedPosters.add(item.posterRelativeFile);
            // Legacy videos without their own poster may still use the theme fallback frame.
            if (item.isDynamic() && item.posterRelativeFile.isEmpty()) plan.protectedPosters.add("wallpaper.jpg");
        }
        plan.paths.removeAll(plan.protectedPosters);
        plan.paths.remove("");
        return plan;
    }
}
