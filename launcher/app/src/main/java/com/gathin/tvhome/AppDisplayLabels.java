package com.gathin.tvhome;

/** Local app-library aliases. Never changes a package's system identity or HOME state. */
final class AppDisplayLabels {
    private AppDisplayLabels() {}

    static String forPackage(String packageName, String originalLabel) {
        if (LauncherAppVisibility.MANAGER.equals(packageName)) return "应用管理";
        return "com.mitv.tvhome".equals(packageName) ? "小米桌面" : originalLabel;
    }
}
