package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackgroundResourceCoordinatorTest {
    @Test public void onlyOneVisualResourceOwnsPlaybackAtATime() {
        BackgroundResourceCoordinator coordinator = new BackgroundResourceCoordinator();
        assertTrue(coordinator.acquire(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER));
        assertFalse(coordinator.acquire(BackgroundResourceCoordinator.Owner.PREVIEW));
        assertTrue(coordinator.isOwner(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER));
        coordinator.release(BackgroundResourceCoordinator.Owner.PREVIEW);
        assertTrue(coordinator.isOwner(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER));
        coordinator.release(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER);
        assertTrue(coordinator.acquire(BackgroundResourceCoordinator.Owner.SCREENSAVER));
    }
}
