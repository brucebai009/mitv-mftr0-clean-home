package com.gathin.tvhome;

/** Small bounded separable blur for an already downsampled opaque wallpaper. */
public final class ArgbBoxBlur {
    private ArgbBoxBlur() { }

    public static int[] blur(int[] source, int width, int height, int radius, int passes) {
        if (source == null || width < 1 || height < 1 || (long) width * height != source.length
                || source.length > 1_048_576 || radius < 0 || radius > 64 || passes < 1 || passes > 3) {
            throw new IllegalArgumentException("Invalid bounded wallpaper blur");
        }
        int[] result = source.clone();
        if (radius == 0) return result;
        int[] scratch = new int[result.length];
        for (int pass = 0; pass < passes; pass++) {
            axis(result, scratch, width, height, radius, true);
            axis(scratch, result, width, height, radius, false);
        }
        return result;
    }

    private static void axis(int[] in, int[] out, int width, int height, int radius, boolean horizontal) {
        int length = horizontal ? width : height;
        int lines = horizontal ? height : width;
        int stride = horizontal ? 1 : width;
        int kernel = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = horizontal ? line * width : line;
            int a = 0, r = 0, g = 0, b = 0;
            for (int k = -radius; k <= radius; k++) {
                int c = in[base + clamp(k, length) * stride];
                a += c >>> 24; r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255;
            }
            for (int x = 0; x < length; x++) {
                out[base + x * stride] = (a / kernel) << 24 | (r / kernel) << 16
                        | (g / kernel) << 8 | b / kernel;
                int old = in[base + clamp(x - radius, length) * stride];
                int next = in[base + clamp(x + radius + 1, length) * stride];
                a += (next >>> 24) - (old >>> 24);
                r += (next >> 16 & 255) - (old >> 16 & 255);
                g += (next >> 8 & 255) - (old >> 8 & 255);
                b += (next & 255) - (old & 255);
            }
        }
    }

    private static int clamp(int n, int length) { return Math.max(0, Math.min(length - 1, n)); }
}
