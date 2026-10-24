package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

/**
 * One boot-time handoff for the explicitly user-enabled Home helper.
 * There is no timer, foreground loop, overlay, or repeated retry: if the firmware does not deliver
 * the broadcast or the user has disabled the helper, the original Xiaomi HOME remains in charge.
 */
public final class HomeBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!RecoveryState.helperEnabled(context) || !RecoveryState.bootRecoveryEnabled(context)) return;
        if (!isHelperEnabledByUser(context)) return;
        try {
            context.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(HomeIdentity.activity(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (android.content.ActivityNotFoundException | SecurityException denied) {
            android.util.Log.w("TvHome.Boot", "Firmware rejected the opt-in boot handoff", denied);
        }
    }

    static boolean isHelperEnabledByUser(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        for (String service : enabled.split(":")) {
            if (HomeIdentity.helper(context).equals(ComponentName.unflattenFromString(service))) return true;
        }
        return false;
    }
}
