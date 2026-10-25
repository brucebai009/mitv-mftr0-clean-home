package com.gathin.tvhome;

/** Narrow, event-driven fallback for the MFTR0 firmware that refuses third-party default HOME. */
final class HomeReturnPolicy {
    static final String STOCK_PACKAGE = "com.mitv.tvhome";
    static final String STOCK_HOME_CLASS = "com.mitv.tvhome.MainActivityUserMode";
    private static final long DUPLICATE_WINDOW_MS = 400;
    private long lastAttempt = -1;

    static boolean canRestoreFromRoot(boolean enabled, boolean interactive, boolean locked, String activePackage) {
        return enabled && interactive && !locked && STOCK_PACKAGE.equals(activePackage);
    }

    boolean shouldReturn(boolean enabled, String packageName, String className,
            boolean interactive, boolean locked, long now) {
        if (!enabled || !interactive || locked || now < 0
                || !STOCK_PACKAGE.equals(packageName) || !STOCK_HOME_CLASS.equals(className)) return false;
        if (lastAttempt >= 0 && now - lastAttempt < DUPLICATE_WINDOW_MS) return false;
        lastAttempt = now;
        return true;
    }
}
