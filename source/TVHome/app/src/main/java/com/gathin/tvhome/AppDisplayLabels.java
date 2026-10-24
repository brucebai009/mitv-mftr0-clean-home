package com.gathin.tvhome;

/** User-facing labels for system apps shown inside TVHome's app library. */
final class AppDisplayLabels {
    private static final String XIAOMI_HOME_PACKAGE = "com.mitv.tvhome";
    private static final String XIAOMI_HOME_LABEL = "小米桌面";

    private AppDisplayLabels() {}

    static String forPackage(String packageName, String platformLabel) {
        return XIAOMI_HOME_PACKAGE.equals(packageName) ? XIAOMI_HOME_LABEL : platformLabel;
    }
}
