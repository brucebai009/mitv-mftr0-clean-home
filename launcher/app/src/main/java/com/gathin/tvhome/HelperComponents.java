package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

/** Only these fixed TVHome-owned components can be changed; no caller-supplied package or class. */
final class HelperComponents {
    private static final Class<?>[] TYPES = {
            HomeOnlyAccessibilityService.class, HomeKeyService.class, HomeBootReceiver.class,
            HomeReturnService.class, HomeReturnBootReceiver.class, HomeReturnRevivalListener.class
    };
    private static final String PREFS = "tvhome_helper_components_backup";
    private HelperComponents() { }

    static Bundle status(Context context) {
        Bundle result = new Bundle();
        for (Class<?> type : TYPES) {
            result.putInt(type.getSimpleName(), context.getPackageManager()
                    .getComponentEnabledSetting(new ComponentName(context, type)));
        }
        return result;
    }

    static synchronized Bundle change(Context context, boolean restore) {
        Bundle result = new Bundle();
        result.putBundle("before", status(context));
        SharedPreferences saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (restore && !saved.getBoolean("saved", false)) {
            result.putBoolean("accepted", false);
            result.putString("error", "No helper component snapshot exists");
            return result;
        }
        if (!restore && !saved.getBoolean("saved", false)) {
            SharedPreferences.Editor backup = saved.edit().putBoolean("saved", true);
            for (Class<?> type : TYPES) backup.putInt(type.getSimpleName(), context.getPackageManager()
                    .getComponentEnabledSetting(new ComponentName(context, type)));
            if (!backup.commit()) throw new IllegalStateException("Cannot save helper component snapshot");
        }
        // Restoring component availability does not re-arm HOME interception or boot launching.
        if (!RecoveryState.disarmSynchronously(context)) {
            throw new IllegalStateException("Cannot persist helper-off state");
        }
        HomeReturnState.setEnabled(context, false);
        boolean accepted = true;
        for (Class<?> type : TYPES) {
            int desired = restore ? saved.getInt(type.getSimpleName(), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (desired < 0 || desired > PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                accepted = false;
                result.putString(type.getSimpleName() + "_error", "Invalid saved component state");
                continue;
            }
            try {
                ComponentName component = new ComponentName(context, type);
                context.getPackageManager().setComponentEnabledSetting(component, desired, PackageManager.DONT_KILL_APP);
                if (context.getPackageManager().getComponentEnabledSetting(component) != desired) accepted = false;
            } catch (RuntimeException error) {
                accepted = false;
                result.putString(type.getSimpleName() + "_error", error.getClass().getSimpleName());
            }
        }
        if (restore && accepted && !saved.edit().clear().commit()) accepted = false;
        result.putBoolean("accepted", accepted);
        result.putBundle("after", status(context));
        return result;
    }
}
