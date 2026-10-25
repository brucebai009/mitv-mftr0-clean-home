package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.io.File;

/** Private positive evidence, scoped to original bytes/file identity and this firmware. Never grants a codec-wide bypass. */
final class HardwarePlaybackProof {
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("verified_hardware_wallpapers", Context.MODE_PRIVATE);
    }
    private static String key(File file) { return file.getAbsolutePath(); }
    private static String stamp(File file) {
        return file.length() + "|" + file.lastModified() + "|" + Build.FINGERPRINT;
    }
    static boolean has(Context context, File file) {
        if (file == null || !file.isFile()) return false;
        return stamp(file).equals(prefs(context).getString(key(file) + ":stamp", ""))
                && !prefs(context).getString(key(file) + ":codec", "").isEmpty()
                && prefs(context).getString(key(file) + ":sha256", "").length() == 64;
    }
    static boolean remember(Context context, File file, String hash, String codec) {
        if (hash == null || !hash.matches("[0-9a-f]{64}") || codec == null || codec.isEmpty()) return false;
        return prefs(context).edit().putString(key(file) + ":stamp", stamp(file))
                .putString(key(file) + ":sha256", hash).putString(key(file) + ":codec", codec).commit();
    }
}
