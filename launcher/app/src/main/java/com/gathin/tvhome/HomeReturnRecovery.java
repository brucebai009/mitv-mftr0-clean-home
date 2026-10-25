package com.gathin.tvhome;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

/** Opt-in, event-driven repair of this APK's one accessibility entry. Never launches an Activity. */
final class HomeReturnRecovery {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String PREFS = "tvhome_home_recovery", KEY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES;
    private static int generation;
    private static boolean pending;
    private HomeReturnRecovery() { }
    static boolean userUnlocked(Context context) {
        UserManager manager = context.getSystemService(UserManager.class);
        return manager != null && manager.isUserUnlocked();
    }
    static boolean hasWritePermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }
    static boolean listenerAllowed(Context context) {
        if (Build.VERSION.SDK_INT < 27) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.isNotificationListenerAccessGranted(new ComponentName(context, HomeReturnRevivalListener.class));
    }
    static boolean eligible(Context context) {
        return userUnlocked(context) && HomeReturnState.enabled(context) && hasWritePermission(context) && listenerAllowed(context);
    }
    static void cancel() { generation++; pending = false; }
    static void resetBudget(Context context) {
        cancel();
        context.getSharedPreferences(PREFS, 0).edit().remove("attempts").commit();
    }
    static void requestListener(Context context) {
        if (!userUnlocked(context) || !HomeReturnState.enabled(context) || !listenerAllowed(context)) return;
        try { NotificationListenerService.requestRebind(new ComponentName(context, HomeReturnRevivalListener.class)); }
        catch (RuntimeException error) { HomeReturnDiagnostics.record(context, "LISTENER_REQUEST_REJECTED", error.getClass().getSimpleName()); }
    }
    static void trigger(Context context, String reason) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            if (pending || !eligible(app)) return;
            pending = true;
            int token = ++generation;
            // Wait for an already-running system bind; do not churn a healthy service.
            MAIN.postDelayed(() -> repair(app, reason, token), 6_000);
        });
    }
    private static void repair(Context context, String reason, int token) {
        if (token != generation) return;
        if (!eligible(context) || (HomeReturnService.isConnected() && HomeReturnState.systemEnabled(context))) {
            pending = false;
            return;
        }
        String own = new ComponentName(context, HomeReturnService.class).flattenToString();
        try {
            HomeServiceList current = new HomeServiceList(Settings.Secure.getString(context.getContentResolver(), KEY));
            SharedPreferences budget = context.getSharedPreferences(PREFS, 0);
            long now = System.currentTimeMillis();
            String saved = budget.getString("attempts", "");
            if (!HomeRecoveryBudget.allowed(saved, now)) {
                pending = false;
                HomeReturnDiagnostics.record(context, "RECOVERY_RATE_LIMITED", reason);
                return;
            }
            if (!budget.edit().putString("attempts", HomeRecoveryBudget.reserve(saved, now)).commit()) {
                throw new IllegalStateException("Cannot persist recovery budget");
            }
            if (current.contains(own)) {
                // Crash/stalled bind: deliver a real removal before re-add, not two coalesced writes.
                write(context, current.without(own));
                HomeReturnDiagnostics.record(context, "REBIND_REMOVE_OWN", reason);
                MAIN.postDelayed(() -> addOwn(context, own, reason, token), 1_500);
            } else addOwn(context, own, reason, token);
        } catch (RuntimeException error) {
            pending = false;
            HomeReturnDiagnostics.record(context, "RECOVERY_REJECTED", reason + ":" + error.getClass().getSimpleName());
        }
    }
    private static void addOwn(Context context, String own, String reason, int token) {
        if (token != generation) return;
        try {
            if (!eligible(context)) return;
            // Re-read now: preserve services added by someone else during the 1.5s gap.
            HomeServiceList latest = new HomeServiceList(Settings.Secure.getString(context.getContentResolver(), KEY));
            write(context, latest.with(own));
            if (Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1
                    && !Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1)) {
                throw new IllegalStateException("Cannot enable accessibility");
            }
            HomeReturnDiagnostics.record(context, "RECOVERY_ADD_OWN", reason);
            // One check, no self-rescheduling loop. Another lifecycle event can retry within budget.
            MAIN.postDelayed(() -> {
                if (token != generation) return;
                HomeReturnDiagnostics.record(context, HomeReturnService.isConnected() ? "RECOVERY_BOUND" : "RECOVERY_UNBOUND", reason);
            }, 6_000);
        } catch (RuntimeException error) {
            HomeReturnDiagnostics.record(context, "RECOVERY_REJECTED", reason + ":" + error.getClass().getSimpleName());
        } finally { if (token == generation) pending = false; }
    }
    private static void write(Context context, String value) {
        if (!Settings.Secure.putString(context.getContentResolver(), KEY, value)) throw new IllegalStateException("Service write refused");
    }
    static Bundle status(Context context) {
        Bundle out = new Bundle();
        out.putBoolean("home_recovery_write_granted", hasWritePermission(context));
        out.putBoolean("home_recovery_listener_allowed", listenerAllowed(context));
        out.putBoolean("home_recovery_listener_bound", HomeReturnRevivalListener.connected);
        out.putBoolean("home_recovery_pending", pending);
        out.putBoolean("home_recovery_user_unlocked", userUnlocked(context));
        return out;
    }
}
