package com.gathin.tvhome;

/** One continuous, single-overshoot path. The actual scroll stays in bounds; only the tiny excess translates. */
final class DockSpringCurve {
    static final int DURATION_MS = 420;
    static final float MAX_OVERSHOOT_DP = 6f;
    private final float distance;
    private final float tension;

    DockSpringCurve(float signedDistance, float density) {
        distance = signedDistance;
        float length = Math.abs(signedDistance);
        // Small moves should not acquire a disproportionately large spring.
        float amplitude = Math.min(MAX_OVERSHOOT_DP * Math.max(0f, density), length * 0.025f);
        float ratio = length > 0 ? amplitude / length : 0f;
        float low = 0f, high = 3f;
        for (int i = 0; i < 28; i++) {
            float mid = (low + high) * 0.5f;
            if (overshootRatio(mid) < ratio) low = mid; else high = mid;
        }
        tension = (low + high) * 0.5f;
    }

    private static float overshootRatio(float s) {
        return 4f * s * s * s / (27f * (s + 1f) * (s + 1f));
    }

    float progress(float time) {
        if (time <= 0f) return 0f;
        if (time >= 1f) return 1f;
        float u = time - 1f;
        return u * u * ((tension + 1f) * u + tension) + 1f;
    }

    int scrollPixels(float time) {
        return Math.round(distance * Math.max(0f, Math.min(1f, progress(time))));
    }

    float translationPixels(float time) {
        // Positive dy means content moves up. Return from its excess in the opposite direction.
        return -distance * Math.max(0f, progress(time) - 1f);
    }

    float contentOffset(float time) { return -distance * progress(time); }
}
