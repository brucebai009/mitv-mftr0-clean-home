package com.gathin.tvhome;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Allow-list shared by the recovery provider and its tests. */
final class RecoveryContract {
    static final String OP_HELPER_ENABLED = "helper_enabled";
    static final String OP_BOOT_RECOVERY = "boot_recovery";
    static final String OP_RESET_GESTURE = "reset_gesture";
    static final String OP_STATUS = "status";
    static final String OP_DISABLE_HELPERS = "disable_helpers";
    static final String OP_RESTORE_HELPERS = "restore_helpers";
    private static final Set<String> OPERATIONS = new HashSet<>(Arrays.asList(
            OP_HELPER_ENABLED, OP_BOOT_RECOVERY, OP_RESET_GESTURE,
            OP_STATUS, OP_DISABLE_HELPERS, OP_RESTORE_HELPERS));

    private RecoveryContract() { }

    static boolean isAllowed(String operation) {
        return operation != null && OPERATIONS.contains(operation);
    }

    static boolean isBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }
}
