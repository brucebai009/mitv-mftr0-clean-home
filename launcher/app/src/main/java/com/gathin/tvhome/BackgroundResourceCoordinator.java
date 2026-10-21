package com.gathin.tvhome;

/** Serializes visual video resources so wallpaper, preview, and screensaver do not play together. */
final class BackgroundResourceCoordinator {
    enum Owner { VIDEO_WALLPAPER, PREVIEW, SCREENSAVER }
    static final BackgroundResourceCoordinator INSTANCE = new BackgroundResourceCoordinator();

    private Owner owner;

    synchronized boolean acquire(Owner requested) {
        if (requested == null || (owner != null && owner != requested)) return false;
        owner = requested;
        return true;
    }

    synchronized void release(Owner requested) {
        if (owner == requested) owner = null;
    }

    synchronized boolean isOwner(Owner requested) {
        return owner == requested;
    }
}
