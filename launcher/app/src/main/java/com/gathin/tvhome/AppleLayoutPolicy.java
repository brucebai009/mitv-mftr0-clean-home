package com.gathin.tvhome;

/** Small, device-independent geometry rules for the V13.1 Apple-style surface. */
final class AppleLayoutPolicy {
    private AppleLayoutPolicy() { }

    static int cardHeightDp(int widthDp) {
        return Math.round(widthDp * 10f / 16f);
    }

    static int dockWidthPx(int availableWidthPx) {
        if (availableWidthPx <= 0) return 0;
        return Math.round(availableWidthPx * 0.93f);
    }

    static boolean isColumnChoice(int columns) {
        return columns >= 4 && columns <= 7;
    }
}
