package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;

/** Lifecycle-owned backdrop; preserves callers' OnShow/OnDismiss listeners, including video cleanup. */
final class FrostedDialog extends Dialog {
    private final Activity owner;
    private FrostedBackdrop.Lease backdrop;
    FrostedDialog(Activity owner) { super(owner); this.owner = owner; setOwnerActivity(owner); }
    @Override public void show() {
        if (isShowing()) return;
        backdrop = FrostedBackdrop.acquire(owner);
        try { super.show(); }
        catch (RuntimeException failure) { releaseBackdrop(); throw failure; }
    }
    @Override protected void onStart() {
        super.onStart();
        if (getWindow() != null) getWindow().setDimAmount(0.16f);
    }
    @Override protected void onStop() { releaseBackdrop(); super.onStop(); }
    @Override public void dismiss() {
        try { super.dismiss(); }
        finally { releaseBackdrop(); }
    }
    private void releaseBackdrop() {
        FrostedBackdrop.Lease old = backdrop; backdrop = null;
        if (old != null) old.close();
    }
}
