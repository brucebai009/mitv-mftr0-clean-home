package com.gathin.tvhome;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Narrow, privileged recovery bridge for the installer. It changes only TVHome-owned preferences;
 * it cannot write arbitrary Settings keys or launch arbitrary Intents.
 */
public final class RecoveryProvider extends ContentProvider {
    static final String AUTHORITY = "com.gathin.tvhome.recovery";
    private static final String PREFS = "tvhome_recovery";

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Context context = getContext();
        if (context == null || context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_SECURE_SETTINGS is required");
        }
        if (!RecoveryContract.isAllowed(method)) throw new IllegalArgumentException("Unsupported recovery operation");
        boolean enabled = extras != null && extras.getBoolean("enabled", false);
        if (!RecoveryContract.OP_RESET_GESTURE.equals(method)
                && extras != null && extras.containsKey("value")
                && !RecoveryContract.isBoolean(extras.getString("value"))) {
            throw new IllegalArgumentException("Only true or false is accepted");
        }
        if (RecoveryContract.OP_RESET_GESTURE.equals(method)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove("gesture_state").apply();
        } else {
            String value = extras != null && extras.containsKey("value")
                    ? extras.getString("value") : Boolean.toString(enabled);
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(method, Boolean.parseBoolean(value)).apply();
        }
        Bundle result = new Bundle();
        result.putBoolean("accepted", true);
        result.putString("operation", method);
        return result;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
