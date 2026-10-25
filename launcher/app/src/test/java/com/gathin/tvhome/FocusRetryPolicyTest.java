package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FocusRetryPolicyTest {
    @Test public void targetRefusalRetriesOnlyWithinTheBoundedLandingWindow() {
        assertTrue(FocusRetryPolicy.shouldRetry(0));
        assertTrue(FocusRetryPolicy.shouldRetry(FocusRetryPolicy.MAX_ATTEMPTS - 1));
        assertFalse(FocusRetryPolicy.shouldRetry(FocusRetryPolicy.MAX_ATTEMPTS));
    }
}
