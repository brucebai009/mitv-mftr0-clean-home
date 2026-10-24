package com.gathin.tvhome;

/** Limits for the shell-only, original-to-compatibility launcher transfer. */
final class TransferPolicy {
    static final String SOURCE = "com.gathin.tvhome";
    static final String TARGET = "com.ts.launcher";
    static final int MAX_METADATA = 512 * 1024;
    static final int MAX_FILES = 256;
    static final long MAX_FILE_BYTES = 512L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    private TransferPolicy() { }

    static String relativeVideo(String path, String root) {
        if (path == null || path.isEmpty()) return "";
        if (root == null || !path.startsWith(root + "/")) throw new IllegalArgumentException("Video is outside source files");
        String relative = path.substring(root.length() + 1);
        if (!relative.matches("videos/[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.[mM][pP]4"))
            throw new IllegalArgumentException("Invalid video path");
        return relative;
    }
}
