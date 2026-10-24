package com.gathin.tvhome.entry;

final class EntryPolicy {
    static final String COMPAT = "com.ts.launcher";
    static final String ORIGINAL = "com.gathin.tvhome";
    static final String CERT = "8edb7ec47cf01743c0059740451a0dc4570b5312fe4c0e06c83595948f95d595";
    enum State { ABSENT, TRUSTED, UNTRUSTED }
    private EntryPolicy() { }
    static String automaticTarget(State compat, State original) {
        if (compat == State.TRUSTED) return COMPAT;
        // Never silently substitute an unrelated application's compatibility package.
        return compat == State.ABSENT && original == State.TRUSTED ? ORIGINAL : null;
    }
    static boolean trustedCertificates(String[] sha256) {
        return sha256 != null && sha256.length == 1 && CERT.equalsIgnoreCase(sha256[0]);
    }
}
