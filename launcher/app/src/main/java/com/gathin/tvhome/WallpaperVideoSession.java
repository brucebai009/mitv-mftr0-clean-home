package com.gathin.tvhome;
interface WallpaperVideoSession {
    interface VisualEvents {
        void approachingEnd();
        void firstFrame();
    }
    default void setVisualEvents(VisualEvents events) { }
    /** One UI-thread snapshot, owned by the recipient; no additional decoder or disk access. */
    default void captureFrame(java.util.function.Consumer<android.graphics.Bitmap> captured) {
        captured.accept(null);
    }
    void start();
    void stop();
    void close();
    /** Playlist transitions wait for the serial decoder release before allocating the next one. */
    default void closeWhenReleased(Runnable afterRelease) {
        close();
        if (afterRelease != null) afterRelease.run();
    }
}
