package com.gathin.tvhome;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;

/**
 * Narrow, privileged recovery bridge for the installer. Only TVHome-owned preferences and the
 * three fixed optional helper components are mutable. No arbitrary Settings keys or Intents.
 */
public final class RecoveryProvider extends ContentProvider {

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null || context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_SECURE_SETTINGS is required");
        }
        if (!RecoveryContract.isAllowed(method)) throw new IllegalArgumentException("Unsupported recovery operation");
        if (RecoveryContract.OP_STATUS.equals(method)
                || RecoveryContract.OP_DISABLE_HELPERS.equals(method)
                || RecoveryContract.OP_RESTORE_HELPERS.equals(method)) {
            // Authenticate BEFORE clearing Binder identity. PackageManager must see the owner app
            // changing its own fixed components, not the remote shell caller changing another app.
            long identity = Binder.clearCallingIdentity();
            try {
                Bundle result = RecoveryContract.OP_STATUS.equals(method) ? status(context)
                        : HelperComponents.change(context, RecoveryContract.OP_RESTORE_HELPERS.equals(method));
                result.putString("operation", method);
                return result;
            } finally { Binder.restoreCallingIdentity(identity); }
        }
        boolean enabled = extras != null && extras.getBoolean("enabled", false);
        if (!RecoveryContract.OP_RESET_GESTURE.equals(method)
                && extras != null && extras.containsKey("value")
                && !RecoveryContract.isBoolean(extras.getString("value"))) {
            throw new IllegalArgumentException("Only true or false is accepted");
        }
        if (RecoveryContract.OP_RESET_GESTURE.equals(method)) {
            RecoveryState.disarm(context);
        } else {
            String value = extras != null && extras.containsKey("value")
                    ? extras.getString("value") : Boolean.toString(enabled);
            RecoveryState.setBoolean(context, method, Boolean.parseBoolean(value));
        }
        Bundle result = new Bundle();
        result.putBoolean("accepted", true);
        result.putString("operation", method);
        return result;
    }

    private Bundle status(Context context) {
        // Reading status must never run Config.load: its theme synchronization can write choices,
        // making a fresh compatibility installation ineligible for import.
        Config config = MainActivity.lastCreatedConfig;
        android.content.SharedPreferences choices = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        result.putBoolean("accepted", true);
        result.putString("package", context.getPackageName());
        result.putBoolean("effective_config_available", config != null);
        if (config != null) {
            result.putInt("columns", config.columns);
            result.putInt("rows_below_dock", config.rowsBelowDock);
        }
        result.putBoolean("static_cover", choices.getBoolean("static_cover", false));
        result.putString("background_mode", choices.getString("background_mode", "image"));
        result.putBoolean("helper_enabled", RecoveryState.helperEnabled(context));
        result.putBoolean("boot_recovery", RecoveryState.bootRecoveryEnabled(context));
        Bundle components = HelperComponents.status(context);
        // content call does not unmarshal nested Bundles when printing. Flatten fixed scalar keys.
        for (String key : components.keySet()) result.putInt(key, components.getInt(key));
        if (config != null) {
            result.putBoolean("animations", config.animations);
            result.putBoolean("advanced_effects", config.advancedEffects);
            result.putBoolean("reduced_motion", config.reducedMotion);
        }
        return result;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
