package com.gathin.tvhome;

import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Ordinary-UID passthrough probe. The caller supplies a real TvInputManager inputId and a visible host;
 * no hidden APIs, shell commands, fake input IDs, or synthetic visual success are used.
 */
final class HdmiPreviewProbe {
    interface Listener { void onResult(PreviewCapabilityClassifier.Result result); }

    private final Context context;
    private final Listener listener;
    private TvView tvView;
    private ViewGroup host;
    private String inputId;
    private boolean tuned;

    HdmiPreviewProbe(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void attach(ViewGroup host, String inputId) {
        close();
        this.host = host;
        this.inputId = inputId;
        if (host == null || inputId == null || inputId.trim().isEmpty()) {
            listener.onResult(PreviewCapabilityClassifier.Result.NO_ACTIVE_SOURCE);
            return;
        }
        TvInputManager manager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        TvInputInfo info = manager == null ? null : manager.getTvInputInfo(inputId);
        if (info == null) {
            listener.onResult(PreviewCapabilityClassifier.Result.NO_ACTIVE_SOURCE);
            return;
        }
        if (!BackgroundResourceCoordinator.INSTANCE.acquire(BackgroundResourceCoordinator.Owner.PREVIEW)) {
            listener.onResult(PreviewCapabilityClassifier.Result.BLOCKED_VENDOR);
            return;
        }
        tvView = new TvView(context);
        tvView.setStreamVolume(0f);
        tvView.setCallback(new TvView.TvInputCallback() {
            @Override public void onTuned(String id, Uri channelUri) {
                tuned = true;
            }

            @Override public void onVideoAvailable(String id) {
                // A callback alone is intentionally only pending; the caller must confirm a real visible frame.
                listener.onResult(PreviewCapabilityClassifier.classify(true, true, tuned, true, false));
            }

            @Override public void onConnectionFailed(String id) {
                listener.onResult(PreviewCapabilityClassifier.Result.BLOCKED_VENDOR);
            }

            @Override public void onVideoUnavailable(String id, int reason) {
                listener.onResult(PreviewCapabilityClassifier.Result.BLOCKED_VENDOR);
            }
        });
        host.addView(tvView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        try {
            Uri passthrough = TvContract.buildChannelUriForPassthroughInput(inputId);
            tvView.tune(inputId, passthrough);
        } catch (SecurityException denied) {
            listener.onResult(PreviewCapabilityClassifier.Result.BLOCKED_PERMISSION);
        } catch (RuntimeException vendorFailure) {
            listener.onResult(PreviewCapabilityClassifier.Result.BLOCKED_VENDOR);
        }
    }

    /** Call only after an actual visible frame was inspected on the TV. */
    void confirmVisualFrame() {
        if (tuned) listener.onResult(PreviewCapabilityClassifier.Result.SUPPORTED_VERIFIED);
    }

    void close() {
        if (tvView != null) {
            tvView.reset();
            if (host != null) host.removeView(tvView);
        }
        tvView = null;
        host = null;
        tuned = false;
        BackgroundResourceCoordinator.INSTANCE.release(BackgroundResourceCoordinator.Owner.PREVIEW);
    }
}
