package com.gathin.tvhome;

/** A few rendered frames or a moving playback clock is insufficient evidence for an original-rate exception. */
final class HardwareDecodeVerdict {
    static boolean pass(int sourceWidth, int sourceHeight, int sourceFps, int outputWidth, int outputHeight,
                        int renderedFrames, long renderedSpanNs, long mediaSpanUs) {
        if (sourceFps <= 0 || sourceWidth != outputWidth || sourceHeight != outputHeight
                || renderedFrames < 90 || renderedSpanNs < 3_000_000_000L || mediaSpanUs < 3_000_000L) return false;
        double actual = (renderedFrames - 1) * 1_000_000_000d / renderedSpanNs;
        return actual >= sourceFps * .95 && actual <= sourceFps * 1.15;
    }
}
