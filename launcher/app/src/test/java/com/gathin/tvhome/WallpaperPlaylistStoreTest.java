package com.gathin.tvhome;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class WallpaperPlaylistStoreTest {
    @Test public void orderedIdsUseJsonAndIgnoreNullOnlyAtWriteBoundary() {
        assertEquals("[\"a\",\"b\"]", WallpaperPlaylistStore.encode(
                Arrays.asList("a", null, "b", "")));
    }

    @Test public void emptyEncodingIsAnIntentNotAnImplicitEnable() {
        assertEquals("[]", WallpaperPlaylistStore.encode(Collections.emptyList()));
    }
}
