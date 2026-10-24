package com.gathin.tvhome;

/** Bounded static-backdrop lens approximation for API30; not a physical renderer. */
public final class ScreenSpaceLens {
    private ScreenSpaceLens() { }

    public static int[] render(int[] background, int bw, int bh, int left, int top, int width, int height,
                               float radius, float edgeBand, float bend, float dispersion) {
        if (background == null || bw < 1 || bh < 1 || (long) bw * bh != background.length
                || background.length > 1_048_576 || width < 1 || height < 1
                || (long) width * height > 262_144 || !Float.isFinite(radius) || radius < 0
                || radius > Math.min(width, height) / 2f || !Float.isFinite(edgeBand) || edgeBand <= 0
                || !Float.isFinite(bend) || bend < 0 || bend > 16
                || !Float.isFinite(dispersion) || dispersion < 0 || dispersion > 2) {
            throw new IllegalArgumentException("Invalid bounded backdrop lens");
        }
        int[] result = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            float px = x + 0.5f - width / 2f, py = y + 0.5f - height / 2f;
            float distance = sdf(px, py, width, height, radius);
            float coverage = clamp(0.5f - distance, 0, 1);
            if (coverage == 0) continue;
            float edge = clamp(1f + distance / edgeBand, 0, 1);
            float nx = 0, ny = 0;
            if (edge > 0 && (bend > 0 || dispersion > 0)) {
                nx = sdf(px + 0.25f, py, width, height, radius)
                        - sdf(px - 0.25f, py, width, height, radius);
                ny = sdf(px, py + 0.25f, width, height, radius)
                        - sdf(px, py - 0.25f, width, height, radius);
                float norm = (float) Math.hypot(nx, ny);
                if (norm > 0.0001f) { nx /= norm; ny /= norm; }
            }
            float displacement = bend * edge * edge;
            float spread = dispersion * edge * edge;
            float gx = left + x, gy = top + y;
            int red = channel(background, bw, bh, gx - nx * (displacement + spread), gy - ny * (displacement + spread), 16);
            int green = channel(background, bw, bh, gx - nx * displacement, gy - ny * displacement, 8);
            int blue = channel(background, bw, bh, gx - nx * (displacement - spread), gy - ny * (displacement - spread), 0);
            result[y * width + x] = Math.round(255 * coverage) << 24 | red << 16 | green << 8 | blue;
        }
        return result;
    }

    private static float sdf(float x, float y, int width, int height, float radius) {
        float qx = Math.abs(x) - (width / 2f - radius);
        float qy = Math.abs(y) - (height / 2f - radius);
        return (float) Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius;
    }

    private static int channel(int[] pixels, int w, int h, float x, float y, int shift) {
        x = clamp(x, 0, w - 1); y = clamp(y, 0, h - 1);
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        float fx = x - x0, fy = y - y0;
        float a = (pixels[y0 * w + x0] >>> shift & 255) * (1 - fx) + (pixels[y0 * w + x1] >>> shift & 255) * fx;
        float b = (pixels[y1 * w + x0] >>> shift & 255) * (1 - fx) + (pixels[y1 * w + x1] >>> shift & 255) * fx;
        return Math.round(a * (1 - fy) + b * fy);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
}
