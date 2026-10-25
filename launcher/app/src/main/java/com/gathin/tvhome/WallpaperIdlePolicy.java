package com.gathin.tvhome;

/** Monotonic, foreground-only timer. No polling, system dream setting, wake lock or background service. */
final class WallpaperIdlePolicy {
    static final String KEY = "wallpaper_idle_minutes";
    private long timeout = 240_000L, lastActivity;
    private boolean eligible, idle;

    static int normalizeMinutes(int value) {
        for (int allowed : new int[]{0, 1, 2, 4, 5, 10, 15, 30}) if (value == allowed) return value;
        return 4;
    }

    void configure(int minutes, long now) {
        long next = normalizeMinutes(minutes) * 60_000L;
        if (timeout != next) { timeout = next; activity(now); }
    }

    void eligible(boolean value, long now) {
        if (eligible != value) { eligible = value; activity(now); }
    }

    void activity(long now) { lastActivity = now; idle = false; }
    boolean idle() { return idle; }
    boolean enabled() { return timeout > 0; }
    long remaining(long now) {
        if (!eligible || !enabled() || idle) return -1;
        return Math.max(0, timeout - Math.max(0, now - lastActivity));
    }
    boolean enterIfDue(long now) {
        if (remaining(now) != 0) return false;
        idle = true;
        return true;
    }
    boolean enterNow() {
        // The user's explicit shortcut is available even when the automatic timer is off.
        if (!eligible || idle) return false;
        idle = true;
        return true;
    }
    static boolean wakeOnlyKey(int code) {
        // Back, navigation, confirm, menu and page keys; HOME/voice/volume/power retain system meaning.
        return code == 4 || code >= 19 && code <= 23 || code == 61 || code == 66
                || code == 82 || code == 92 || code == 93 || code == 160;
    }
}
