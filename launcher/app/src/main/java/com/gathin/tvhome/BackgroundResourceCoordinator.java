package com.gathin.tvhome;

/** Process-local ownership: distinct player instances never share a lease, even for the same role. */
final class BackgroundResourceCoordinator {
    enum Owner { VIDEO_WALLPAPER, PREVIEW, SCREENSAVER }
    static final BackgroundResourceCoordinator INSTANCE = new BackgroundResourceCoordinator();
    private Owner owner;
    private Object token;

    synchronized boolean acquire(Owner requested, Object instance) {
        if (requested == null || instance == null || (token != null && token != instance)) return false;
        owner = requested;
        token = instance;
        return true;
    }

    synchronized void release(Object instance) {
        if (instance != null && token == instance) { owner = null; token = null; }
    }

    synchronized boolean isOwner(Owner requested, Object instance) {
        return instance != null && token == instance && owner == requested;
    }
}
