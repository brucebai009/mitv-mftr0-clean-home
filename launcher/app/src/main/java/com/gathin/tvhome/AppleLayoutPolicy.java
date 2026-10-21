package com.gathin.tvhome;

/** Small, device-independent geometry rules for the final Apple-style surface. */
final class AppleLayoutPolicy {
    private AppleLayoutPolicy() { }

    static int cardHeightDp(int widthDp) {
        return Math.round(widthDp * 10f / 16f);
    }

    static int dockWidthPx(int availableWidthPx) {
        if (availableWidthPx <= 0) return 0;
        // The parent already applies the 5%+ horizontal safe insets; do not add a second
        // arbitrary cap that creates a visibly narrow, clipped-looking dock.
        return availableWidthPx;
    }

    static boolean isColumnChoice(int columns) {
        return columns >= 4 && columns <= 7;
    }
}
