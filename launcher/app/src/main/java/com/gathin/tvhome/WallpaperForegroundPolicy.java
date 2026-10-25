package com.gathin.tvhome;

/** Vendor system panels may leave the underlying Activity resumed while taking window focus. */
final class WallpaperForegroundPolicy {
    private boolean playing;
    enum Change { START, STOP, NONE }
    Change update(boolean resumed, boolean focused) {
        return update(resumed, focused, false, false);
    }

    Change update(boolean resumed, boolean focused, boolean ownMenu, boolean separatePage) {
        // A source/settings popup owns focus but is still part of Home. External windows and
        // the app library are different pages; a stale menu must never override onPause.
        boolean desired = resumed && !separatePage && (focused || ownMenu);
        if (desired == playing) return Change.NONE;
        playing = desired;
        return playing ? Change.START : Change.STOP;
    }
}
