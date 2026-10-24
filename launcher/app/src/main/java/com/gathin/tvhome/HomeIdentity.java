package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;

final class HomeIdentity {
    private HomeIdentity() { }
    static ComponentName activity(Context context) {
        return new ComponentName(context, MainActivity.class);
    }
    static ComponentName helper(Context context) {
        return new ComponentName(context, HomeOnlyAccessibilityService.class);
    }
}
