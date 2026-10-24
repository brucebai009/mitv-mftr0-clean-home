package com.gathin.tvhome;

import android.content.Context;

/**
 * Small, TVHome-owned state shared by the optional Home helper, boot receiver, and recovery bridge.
 * It is never a substitute for the system accessibility setting: the receiver also checks that the
 * user-enabled service is still present before starting TVHome.
 */
final class RecoveryState {
    private static final String PREFS = "tvhome_recovery";
    private static final String HELPER_ENABLED = "helper_enabled";
    private static final String BOOT_RECOVERY = "boot_recovery";

    private RecoveryState() { }

    static void markHelperEnabled(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(HELPER_ENABLED, helperEnabledOrUnconfigured(context))
                .putBoolean(BOOT_RECOVERY, bootEnabledOrUnconfigured(context))
                .apply();
    }

    private static boolean helperEnabledOrUnconfigured(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(HELPER_ENABLED, true);
    }

    private static boolean bootEnabledOrUnconfigured(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(BOOT_RECOVERY, true);
    }

    static boolean helperEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HELPER_ENABLED, false);
    }

    static boolean legacyHelperEnabled(Context context) {
        // Preserve an already user-enabled legacy service until explicitly disarmed. Reading this
        // flag must not opt the legacy service into the newer boot-recovery mechanism.
        return helperEnabledOrUnconfigured(context);
    }

    static boolean bootRecoveryEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(BOOT_RECOVERY, false);
    }

    static void setBoolean(Context context, String operation, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(operation, enabled).apply();
    }

    static void disarm(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(HELPER_ENABLED, false)
                .putBoolean(BOOT_RECOVERY, false)
                .remove("gesture_state")
                .apply();
    }

    static boolean disarmSynchronously(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(HELPER_ENABLED, false).putBoolean(BOOT_RECOVERY, false)
                .remove("gesture_state").commit();
    }
}
