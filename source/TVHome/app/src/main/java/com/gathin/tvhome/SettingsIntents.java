package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Vendor routing shared by Home and launcher settings; task ownership stays with the caller. */
final class SettingsIntents {
    private SettingsIntents() { }
    static Intent resolve(Context context, String action) {
        DeviceProfile profile = DeviceProfile.load(context);
        if (profile.matchesDevice() && (Settings.ACTION_WIFI_SETTINGS.equals(action)
                || Settings.ACTION_SETTINGS.equals(action))) {
            Intent vendor = Settings.ACTION_WIFI_SETTINGS.equals(action) ? profile.wifiIntent() : profile.settingsIntent();
            if (vendor != null && vendor.resolveActivity(context.getPackageManager()) != null) return vendor;
        }
        Intent standard = new Intent(action);
        if (standard.resolveActivity(context.getPackageManager()) != null) return standard;
        Intent vendor = new Intent().setComponent(new ComponentName("com.xiaomi.mitv.settings",
                "com.xiaomi.mitv.settings.entry.MainActivity"));
        return vendor.resolveActivity(context.getPackageManager()) == null ? standard : vendor;
    }
    static boolean isSettings(Intent intent) {
        String action = intent.getAction(); ComponentName component = intent.getComponent();
        return (action != null && action.startsWith("android.settings."))
                || (component != null && "com.xiaomi.mitv.settings".equals(component.getPackageName()));
    }
    static Intent fromActivity(Context context, Intent request) {
        Intent target = new Intent(request);
        if (target.getComponent() == null && target.getAction() != null
                && target.getAction().startsWith("android.settings.")) target = resolve(context, target.getAction());
        if (isSettings(target)) target.setFlags(target.getFlags() & ~(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        return target;
    }
}
