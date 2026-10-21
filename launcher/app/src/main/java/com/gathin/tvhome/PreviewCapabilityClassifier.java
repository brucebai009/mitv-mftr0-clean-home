package com.gathin.tvhome;

/** Evidence classifier for the ordinary-UID passthrough preview probe. */
final class PreviewCapabilityClassifier {
    enum Result { SUPPORTED_VERIFIED, API_CONNECTED_VISUAL_PENDING, BLOCKED_PERMISSION, BLOCKED_VENDOR, NO_ACTIVE_SOURCE }

    private PreviewCapabilityClassifier() { }

    static Result classify(boolean activeSource, boolean inputId, boolean tuneAccepted,
                           boolean callbackReceived, boolean visualObserved) {
        return classify(activeSource, inputId, tuneAccepted, callbackReceived, visualObserved, false);
    }

    static Result classify(boolean activeSource, boolean inputId, boolean tuneAccepted,
                           boolean callbackReceived, boolean visualObserved, boolean vendorFailure) {
        if (!activeSource || !inputId) return Result.NO_ACTIVE_SOURCE;
        if (vendorFailure) return Result.BLOCKED_VENDOR;
        if (!tuneAccepted) return Result.BLOCKED_PERMISSION;
        if (!callbackReceived || !visualObserved) return Result.API_CONNECTED_VISUAL_PENDING;
        return Result.SUPPORTED_VERIFIED;
    }
}
