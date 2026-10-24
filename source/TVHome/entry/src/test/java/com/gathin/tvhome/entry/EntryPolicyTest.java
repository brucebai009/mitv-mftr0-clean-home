package com.gathin.tvhome.entry;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.gathin.tvhome.entry.EntryPolicy.State.*;

public class EntryPolicyTest {
    @Test public void migratedCompatibilityInstallationHasPriority() {
        assertEquals(EntryPolicy.COMPAT, EntryPolicy.automaticTarget(TRUSTED, TRUSTED));
    }
    @Test public void absentCompatibilityCanUseOwnedOriginal() {
        assertEquals(EntryPolicy.ORIGINAL, EntryPolicy.automaticTarget(ABSENT, TRUSTED));
        assertNull(EntryPolicy.automaticTarget(ABSENT, UNTRUSTED));
        assertNull(EntryPolicy.automaticTarget(ABSENT, ABSENT));
    }
    @Test public void unrelatedCompatibilityIsNeverLaunchedOrSilentlyBypassed() {
        assertNull(EntryPolicy.automaticTarget(UNTRUSTED, TRUSTED));
    }
    @Test public void certificateMustMatchExactlyOneCurrentSigner() {
        assertTrue(EntryPolicy.trustedCertificates(new String[]{EntryPolicy.CERT}));
        assertFalse(EntryPolicy.trustedCertificates(new String[]{"unrelated"}));
        assertFalse(EntryPolicy.trustedCertificates(new String[]{EntryPolicy.CERT, "unrelated"}));
        assertFalse(EntryPolicy.trustedCertificates(null));
    }
}
