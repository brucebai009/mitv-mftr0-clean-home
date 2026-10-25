package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;

/** Separate from the retired key-filter helper. Disabled by default and synchronously disarmed. */
final class HomeReturnState {
    private static final String PREFS = "tvhome_home_return";
    private HomeReturnState() { }

    static boolean enabled(Context context) {
        if (!HomeReturnRecovery.userUnlocked(context)) return false;
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false);
    }

    static void setEnabled(Context context, boolean enabled) {
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", enabled).commit()) throw new IllegalStateException("Cannot save HOME return state");
        if (!enabled) HomeReturnRecovery.cancel();
    }

    static boolean systemEnabled(Context context) {
        if (Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
        String value = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (value == null) return false;
        ComponentName own = new ComponentName(context, HomeReturnService.class);
        for (String entry : value.split(":")) if (own.equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }

    static Bundle configure(Context context, boolean enabled) {
        // Every route, including recovery/restore, leaves the old key filters disarmed.
        if (!RecoveryState.disarmSynchronously(context)) throw new IllegalStateException("Cannot disarm legacy helper");
        setEnabled(context, false);
        PackageManager pm = context.getPackageManager();
        for (Class<?> type : new Class<?>[]{HomeOnlyAccessibilityService.class, HomeKeyService.class, HomeBootReceiver.class}) {
            ComponentName old = new ComponentName(context, type);
            if (pm.getComponentEnabledSetting(old) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(old, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        int desired = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        for (Class<?> type : new Class<?>[]{HomeReturnService.class, HomeReturnBootReceiver.class, HomeReturnRevivalListener.class}) {
            ComponentName component = new ComponentName(context, type);
            if (pm.getComponentEnabledSetting(component) != desired) {
                pm.setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP);
            }
        }
        setEnabled(context, enabled);
        if (enabled) {
            HomeReturnRecovery.resetBudget(context);
            HomeReturnRecovery.requestListener(context);
            HomeReturnRecovery.trigger(context, "EXPLICIT_ENABLE");
        }
        Bundle result = status(context);
        result.putBoolean("accepted", true);
        return result;
    }

    static Intent launcherIntent(Context context) {
        // Ordinary app task: the firmware refuses this APK as a system HOME task.
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(HomeIdentity.activity(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    static Bundle status(Context context) {
        Bundle result = new Bundle();
        result.putBoolean("home_return_enabled", enabled(context));
        result.putBoolean("home_return_system_enabled", systemEnabled(context));
        result.putBoolean("home_return_bound", HomeReturnService.isConnected());
        result.putString("home_return_mode", "ASSISTED_WINDOW_RETURN");
        result.putInt("home_return_attempts", HomeReturnService.attempts);
        result.putInt("home_return_window_events", HomeReturnService.windowEvents);
        result.putString("home_return_last_window_package", HomeReturnService.lastWindowPackage);
        result.putString("home_return_last_window_class", HomeReturnService.lastWindowClass);
        result.putString("home_return_last_result", HomeReturnService.lastResult);
        result.putAll(HomeReturnRecovery.status(context));
        result.putAll(HomeReturnDiagnostics.status(context));
        return result;
    }
}
