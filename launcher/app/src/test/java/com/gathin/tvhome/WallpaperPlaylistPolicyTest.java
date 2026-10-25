package com.gathin.tvhome;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class WallpaperPlaylistPolicyTest {
    private WallpaperCandidate video(String id) {
        return new WallpaperCandidate(id, id, WallpaperCandidate.Type.VIDEO, "videos/" + id + ".mp4",
                "", "", 1920, 1080, 30, 1000, "", Collections.emptyList());
    }

    @Test public void savedOrderWinsAndInvalidDuplicateIdsAreRemoved() {
        List<WallpaperCandidate> visible = Arrays.asList(video("a"), video("b"), video("c"));
        assertEquals(Arrays.asList("c", "a"), WallpaperPlaylistPolicy.normalizeSelection(
                visible, Arrays.asList("c", "missing", "a", "c")));
    }

    @Test public void firstSelectionUsesCatalogOrder() {
        assertEquals(Arrays.asList("a", "b"), WallpaperPlaylistPolicy.initialSelection(
                Arrays.asList(video("a"), video("b"))));
    }

    @Test public void newlySelectedItemsAppendInDisplayOrder() {
        List<WallpaperCandidate> visible = Arrays.asList(video("a"), video("b"), video("c"));
        // Keep the surviving saved item first; newly checked items append in display order.
        assertEquals(Arrays.asList("c", "b"), WallpaperPlaylistPolicy.reconcileSelection(
                visible, Arrays.asList("c", "a"), Arrays.asList("c", "b")));
    }

    @Test public void nextWrapsAndEmptyCannotEnable() {
        List<String> ids = Arrays.asList("a", "b", "c");
        assertEquals("b", WallpaperPlaylistPolicy.nextId(ids, "a"));
        assertEquals("a", WallpaperPlaylistPolicy.nextId(ids, "c"));
        assertEquals("a", WallpaperPlaylistPolicy.nextId(ids, "missing"));
        assertFalse(WallpaperPlaylistPolicy.canEnable(Collections.emptyList()));
        assertTrue(WallpaperPlaylistPolicy.usesNaturalCompletion(ids));
        assertFalse(WallpaperPlaylistPolicy.usesNaturalCompletion(Collections.singletonList("a")));
    }
}
