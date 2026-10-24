package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class BackgroundResourceCoordinatorTest {
    @Test public void sameRoleDifferentPlayersCannotDecodeTogether() {
        BackgroundResourceCoordinator c = new BackgroundResourceCoordinator();
        Object a = new Object(), b = new Object();
        assertTrue(c.acquire(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, a));
        assertTrue(c.acquire(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, a));
        assertFalse(c.acquire(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, b));
        assertFalse(c.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, b));
        c.release(b);
        assertTrue(c.isOwner(BackgroundResourceCoordinator.Owner.VIDEO_WALLPAPER, a));
        c.release(a);
        assertTrue(c.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, b));
        c.release(a); // a delayed callback from the old owner must not release b
        assertTrue(c.isOwner(BackgroundResourceCoordinator.Owner.PREVIEW, b));
        c.release(b);
        assertTrue(c.acquire(BackgroundResourceCoordinator.Owner.SCREENSAVER, a));
    }
    @Test public void nullIsNeverAnOwner() {
        BackgroundResourceCoordinator c = new BackgroundResourceCoordinator();
        assertFalse(c.acquire(null, new Object()));
        assertFalse(c.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, null));
        c.release(null);
        assertFalse(c.isOwner(null, null));
    }
}
