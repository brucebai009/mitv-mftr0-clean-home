package com.gathin.tvhome;

/** Identity-based exclusions shared by every launcher app picker. Never hide arbitrary blank labels. */
final class LauncherAppVisibility {
    static final String MANAGER = "com.elton.tvmanager";
    private LauncherAppVisibility() { }
    static boolean hidden(String pkg, String current) {
        return pkg != null && (pkg.equals(current) || "com.gathin.tvhome".equals(pkg)
                || "com.gathin.tvhome.entry".equals(pkg) || "com.mitv.tvhome".equals(pkg));
    }
}
