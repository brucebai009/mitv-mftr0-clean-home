package com.gathin.tvhome;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small local milestone journal. No UI text, notification payloads, or crash dump contents. */
final class HomeReturnDiagnostics {
    private static final String PREFS = "tvhome_home_return_diagnostics";
    private static boolean foreground;
    private HomeReturnDiagnostics() { }
    static synchronized void record(Context context, String kind, String detail) {
        Log.i("TvHome.HomeReturn", kind + " " + detail);
        if (!HomeReturnRecovery.userUnlocked(context)) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
            JSONArray old = new JSONArray(prefs.getString("journal", "[]"));
            JSONArray next = new JSONArray();
            for (int i = Math.max(0, old.length() - 31); i < old.length(); i++) next.put(old.get(i));
            JSONObject entry = new JSONObject().put("utcMs", System.currentTimeMillis())
                    .put("pid", Process.myPid()).put("kind", kind).put("detail", detail);
            next.put(entry);
            SharedPreferences.Editor edit = prefs.edit().putString("journal", next.toString())
                    .putString("last", kind + ":" + detail);
            if (kind.equals("LAUNCH_REQUEST")) edit.putLong("total_requests", prefs.getLong("total_requests", 0) + 1);
            edit.apply();
        } catch (RuntimeException | org.json.JSONException error) {
            Log.w("TvHome.HomeReturn", "journal unavailable " + error.getClass().getSimpleName());
        }
    }
    static void foreground(Context context, boolean visible) {
        if (foreground == visible) return;
        foreground = visible;
        record(context, visible ? "FOREGROUND_CONFIRMED" : "FOREGROUND_LOST", "MainActivity");
    }
    static Bundle status(Context context) {
        Bundle out = new Bundle();
        if (!HomeReturnRecovery.userUnlocked(context)) return out;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        out.putLong("home_return_total_requests", prefs.getLong("total_requests", 0));
        out.putString("home_return_journal", prefs.getString("journal", "[]"));
        out.putString("home_return_last_milestone", prefs.getString("last", "NONE"));
        out.putInt("home_return_process_id", Process.myPid());
        out.putBoolean("home_return_foreground", foreground);
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ActivityManager am = context.getSystemService(ActivityManager.class);
                JSONArray exits = new JSONArray();
                if (am != null) for (ApplicationExitInfo exit : am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5)) {
                    exits.put(new JSONObject().put("utcMs", exit.getTimestamp()).put("reason", exit.getReason())
                            .put("status", exit.getStatus()).put("importance", exit.getImportance())
                            .put("process", exit.getProcessName()));
                }
                out.putString("home_return_recent_exits", exits.toString());
            } catch (RuntimeException | org.json.JSONException error) {
                out.putString("home_return_recent_exits", "UNAVAILABLE_" + error.getClass().getSimpleName());
            }
        }
        return out;
    }
}
