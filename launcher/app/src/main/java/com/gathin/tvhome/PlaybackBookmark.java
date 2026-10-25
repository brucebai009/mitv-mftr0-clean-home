package com.gathin.tvhome;

/** One current source per role. A replaced video cannot inherit another video's position. */
final class PlaybackBookmark {
    interface Store {
        String source();
        long position();
        void save(String source, long position);
    }
    private final String source;
    private final Store store;

    PlaybackBookmark(String source, Store store) { this.source = source; this.store = store; }

    long restore(long duration) {
        if (!source.equals(store.source()) || duration <= 0) return 0;
        long position = store.position();
        return position >= 0 && position < duration ? position : 0;
    }

    void remember(long position, long duration, boolean started) {
        // Cancelled preparation/seek must not overwrite a valid bookmark with zero.
        if (started && duration > 0 && position >= 0) store.save(source, position % duration);
    }
}
