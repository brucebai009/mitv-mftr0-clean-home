package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;
import java.util.List;

final class DockPreferences {
    private static final String KEY = "dock_selection_v1";
    static List<String> load(Context context) {
        try {
            return DockSelection.decode(context.getSharedPreferences(Config.SETTINGS_NAME, 0).getString(KEY, null));
        } catch (RuntimeException invalid) {
            Log.w("TvHome.Dock", "Keeping invalid stored selection unchanged", invalid);
            return null;
        }
    }
    static boolean save(Context context, List<String> packages) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, 0).edit()
                .putString(KEY, DockSelection.encode(packages)).commit();
    }
}
