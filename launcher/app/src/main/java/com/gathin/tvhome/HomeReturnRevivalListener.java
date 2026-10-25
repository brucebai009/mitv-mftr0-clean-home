package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;

/** Only connection callbacks are used; no notification query, payload handling, or cancellation. */
public final class HomeReturnRevivalListener extends NotificationListenerService {
    static volatile boolean connected;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean watching;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { HomeReturnRecovery.trigger(HomeReturnRevivalListener.this, "SERVICE_LIST_CHANGED"); }
    };
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            HomeReturnRecovery.trigger(context, intent.getAction());
            HomeReturnService.checkStockLater("WAKE_OR_UNLOCK");
        }
    };
    @Override public void onListenerConnected() {
        connected = true;
        HomeReturnDiagnostics.record(this, "REVIVAL_CONNECTED", "notification system binding");
        if (!watching) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_UNLOCKED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(wake, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(wake, filter);
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer);
            watching = true;
        }
        HomeReturnRecovery.trigger(this, "NOTIFICATION_REBIND");
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        HomeReturnDiagnostics.record(this, "REVIVAL_DISCONNECTED", "notification system binding");
        // The platform owns re-binding. No alarm, foreground service, or retry loop.
    }
    @Override public void onDestroy() {
        connected = false;
        if (watching) {
            unregisterReceiver(wake);
            getContentResolver().unregisterContentObserver(observer);
            watching = false;
        }
        super.onDestroy();
    }
}
