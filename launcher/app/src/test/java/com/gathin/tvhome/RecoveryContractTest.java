package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecoveryContractTest {
    @Test public void onlyOwnRecoveryOperationsAreAllowed() {
        assertTrue(RecoveryContract.isAllowed(RecoveryContract.OP_HELPER_ENABLED));
        assertTrue(RecoveryContract.isAllowed(RecoveryContract.OP_BOOT_RECOVERY));
        assertTrue(RecoveryContract.isAllowed(RecoveryContract.OP_RESET_GESTURE));
        assertFalse(RecoveryContract.isAllowed("settings_put"));
        assertFalse(RecoveryContract.isAllowed("start_arbitrary_intent"));
        assertFalse(RecoveryContract.isAllowed(""));
    }

    @Test public void onlyBooleanValuesAreAccepted() {
        assertTrue(RecoveryContract.isBoolean("true"));
        assertTrue(RecoveryContract.isBoolean("false"));
        assertFalse(RecoveryContract.isBoolean("1"));
        assertFalse(RecoveryContract.isBoolean("com.malicious.intent"));
    }
}
