package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PreviewCapabilityClassifierTest {
    @Test public void noSourceIsDistinctFromPermissionBlock() {
        assertEquals(PreviewCapabilityClassifier.Result.NO_ACTIVE_SOURCE,
                PreviewCapabilityClassifier.classify(false, false, false, false, false));
        assertEquals(PreviewCapabilityClassifier.Result.BLOCKED_PERMISSION,
                PreviewCapabilityClassifier.classify(true, true, false, false, false));
    }

    @Test public void callbackWithoutVisualIsNotClaimedAsSupported() {
        assertEquals(PreviewCapabilityClassifier.Result.API_CONNECTED_VISUAL_PENDING,
                PreviewCapabilityClassifier.classify(true, true, true, true, false));
        assertEquals(PreviewCapabilityClassifier.Result.SUPPORTED_VERIFIED,
                PreviewCapabilityClassifier.classify(true, true, true, true, true));
    }

    @Test public void vendorFailureIsReportedSeparately() {
        assertEquals(PreviewCapabilityClassifier.Result.BLOCKED_VENDOR,
                PreviewCapabilityClassifier.classify(true, true, false, false, false, true));
    }
}
