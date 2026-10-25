package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackBookmarkTest {
    private static class Store implements PlaybackBookmark.Store {
        String key = ""; long time;
        public String source() { return key; }
        public long position() { return time; }
        public void save(String source, long position) { key = source; time = position; }
    }
    @Test public void pageExitAndNewPlayerResumeSamePosition() {
        Store store = new Store();
        new PlaybackBookmark("videoA:100:123", store).remember(12345, 60000, true);
        assertEquals(12345, new PlaybackBookmark("videoA:100:123", store).restore(60000));
    }
    @Test public void cancelledPrepareDoesNotEraseEarlierPosition() {
        Store store = new Store(); PlaybackBookmark state = new PlaybackBookmark("A", store);
        state.remember(12000, 60000, true);
        state.remember(0, 60000, false);
        assertEquals(12000, state.restore(60000));
    }
    @Test public void changedSourceDoesNotSeekUsingOldVideo() {
        Store store = new Store(); new PlaybackBookmark("A", store).remember(12000, 60000, true);
        assertEquals(0, new PlaybackBookmark("B", store).restore(60000));
    }
    @Test public void replacedSamePathHasDifferentIdentity() {
        Store store = new Store(); new PlaybackBookmark("A:100:123", store).remember(12000, 60000, true);
        assertEquals(0, new PlaybackBookmark("A:100:456", store).restore(60000));
    }
    @Test public void endBoundaryWrapsNaturally() {
        Store store = new Store(); PlaybackBookmark state = new PlaybackBookmark("A", store);
        state.remember(60000, 60000, true); assertEquals(0, state.restore(60000));
        state.remember(60017, 60000, true); assertEquals(17, state.restore(60000));
    }
    @Test public void invalidReadDoesNotOverwriteGoodBookmark() {
        Store store = new Store(); PlaybackBookmark state = new PlaybackBookmark("A", store);
        state.remember(12000, 60000, true);
        state.remember(-1, 60000, true); state.remember(0, 0, true);
        assertEquals(12000, state.restore(60000)); assertEquals(0, state.restore(5000));
    }
}
