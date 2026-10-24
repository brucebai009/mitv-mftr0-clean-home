package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/** Resume may replace only files from the exact previous import, never an unknown target file. */
final class TransferStaging {
    static void checkInventory(File root, Set<String> allowed) throws IOException {
        File canonicalRoot = root.getCanonicalFile();
        inspect(canonicalRoot, canonicalRoot, allowed);
    }
    private static void inspect(File root, File directory, Set<String> allowed) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot enumerate staged files");
        for (File file : files) {
            if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) throw new IOException("Symlink in staged files");
            String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            if (file.isDirectory() && (relative.equals("videos") || relative.equals("wallpapers"))) {
                inspect(root,file,allowed);
            } else if (!file.isFile() || !allowed.contains(relative)) {
                throw new IOException("Unowned target file: " + relative);
            }
        }
    }
}
