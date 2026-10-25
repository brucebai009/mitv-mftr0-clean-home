package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restore service readiness. Foreground changes require an active stock-window check. */
public final class HomeReturnBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                || !HomeReturnRecovery.userUnlocked(context) || !HomeReturnState.enabled(context)) return;
        HomeReturnDiagnostics.record(context, "BOOT_COMPLETED", "restore service; no unconditional Activity start");
        HomeReturnRecovery.requestListener(context);
        HomeReturnRecovery.trigger(context, "BOOT_COMPLETED");
        HomeReturnService.checkStockLater("BOOT_COMPLETED");
    }
}
