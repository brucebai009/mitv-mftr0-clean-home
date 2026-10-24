package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Additive, recoverable catalog for image and video wallpapers. */
final class WallpaperCatalog {
    static final int SCHEMA_VERSION = 1;
    static final String FILE_NAME = "wallpaper_catalog.json";

    private WallpaperCatalog() { }

    static List<WallpaperCandidate> load(File root) {
        if (root == null || !root.isDirectory()) return Collections.singletonList(WallpaperCandidate.defaultBackground());
        File metadata = new File(root, FILE_NAME);
        try {
            String text = readText(metadata);
            JSONObject document = new JSONObject(text);
            if (document.optInt("schemaVersion", -1) != SCHEMA_VERSION) throw new IOException("Unsupported catalog schema");
            JSONArray entries = document.optJSONArray("items");
            if (entries == null) throw new IOException("Catalog items missing");
            List<WallpaperCandidate> result = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                WallpaperCandidate candidate = WallpaperCandidate.fromJson(entries.getJSONObject(i));
                if (!validCandidate(root, candidate)) throw new IOException("Invalid catalog path or media");
                result.add(candidate);
            }
            ensureDefault(result);
            mergeScannedEntries(root, result);
            return result;
        } catch (Exception ignored) {
            return scan(root);
        }
    }

    static void upsert(File root, WallpaperCandidate candidate) throws IOException {
        if (root == null || !root.isDirectory() || candidate == null || !validCandidate(root, candidate)) {
            throw new IOException("Invalid wallpaper catalog entry");
        }
        List<WallpaperCandidate> entries = new ArrayList<>(load(root));
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).id.equals(candidate.id)) entries.remove(i);
        }
        entries.add(candidate);
        save(root, entries);
    }

    static void save(File root, List<WallpaperCandidate> entries) throws IOException {
        if (root == null || !root.isDirectory()) throw new IOException("Wallpaper root unavailable");
        List<WallpaperCandidate> safe = new ArrayList<>();
        if (entries != null) for (WallpaperCandidate candidate : entries) {
            if (candidate != null && validCandidate(root, candidate)) safe.add(candidate);
        }
        ensureDefault(safe);
        JSONObject document = new JSONObject();
        try {
            document.put("schemaVersion", SCHEMA_VERSION);
            JSONArray values = new JSONArray();
            for (WallpaperCandidate candidate : safe) values.put(candidate.toJson());
            document.put("items", values);
        } catch (org.json.JSONException impossible) {
            throw new IOException("Could not encode wallpaper catalog", impossible);
        }
        File target = new File(root, FILE_NAME);
        File temporary = new File(root, "." + FILE_NAME + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(document.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (temporary.isFile()) temporary.delete();
        }
    }

    static File resolve(File root, String relative) {
        if (root == null || !safeRelative(relative)) return null;
        try {
            File base = root.getCanonicalFile();
            File file = new File(root, relative).getCanonicalFile();
            String prefix = base.getPath() + File.separator;
            return file.getPath().startsWith(prefix) ? file : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    static boolean safeRelative(String relative) {
        if (relative == null || relative.isEmpty() || new File(relative).isAbsolute()) return false;
        if (relative.indexOf('\0') >= 0 || relative.startsWith("/") || relative.startsWith("\\")) return false;
        String normalized = relative.replace('\\', '/');
        for (String part : normalized.split("/")) if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        return !normalized.contains(":");
    }

    static String sha256(File file) throws IOException { return digest(file, "SHA-256"); }

    static WallpaperCandidate imageFor(File root, File file, String title) throws IOException {
        String relative = relativePath(root, file);
        return new WallpaperCandidate("image-" + shortHash(file), title, WallpaperCandidate.Type.IMAGE,
                relative, "", sha256(file), 0, 0, 0, 0, "", Collections.emptyList());
    }

    static WallpaperCandidate videoFor(File root, File file, String title) throws IOException {
        String relative = relativePath(root, file);
        return new WallpaperCandidate("video-" + shortHash(file), title, WallpaperCandidate.Type.VIDEO,
                relative, "", sha256(file), 0, 0, 0, 0, "Visualdon", Collections.emptyList());
    }

    private static boolean validCandidate(File root, WallpaperCandidate candidate) {
        if (candidate.type == WallpaperCandidate.Type.DEFAULT) return candidate.relativeFile.isEmpty();
        File media = resolve(root, candidate.relativeFile);
        if (media == null || !media.isFile() || media.length() <= 0) return false;
        if (candidate.type == WallpaperCandidate.Type.VIDEO && !candidate.relativeFile.toLowerCase(Locale.ROOT).endsWith(".mp4")) return false;
        if (candidate.type == WallpaperCandidate.Type.IMAGE && !WallpaperImport.supportedExtension(candidate.relativeFile)) return false;
        if (!candidate.posterRelativeFile.isEmpty() && resolve(root, candidate.posterRelativeFile) == null) return false;
        return candidate.sha256.isEmpty() || candidate.sha256.equalsIgnoreCase(safeDigest(media));
    }

    private static List<WallpaperCandidate> scan(File root) {
        List<WallpaperCandidate> result = new ArrayList<>();
        File wallpaper = new File(root, Config.WALLPAPER_NAME);
        if (wallpaper.isFile()) result.add(image("legacy-wallpaper", "现有背景", "wallpaper.jpg", root));
        File images = new File(root, "wallpapers");
        File[] imageFiles = images.listFiles((dir, name) -> WallpaperImport.supportedExtension(name));
        if (imageFiles != null) {
            java.util.Arrays.sort(imageFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            int number = 1;
            for (File file : imageFiles) result.add(image("image-" + number, "自定义壁纸 " + number,
                    "wallpapers/" + file.getName(), root));
        }
        File videos = new File(root, "videos");
        File[] videoFiles = videos.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (videoFiles != null) {
            java.util.Arrays.sort(videoFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : videoFiles) result.add(new WallpaperCandidate(
                    "video-" + shortHash(file), "动态壁纸", WallpaperCandidate.Type.VIDEO,
                    "videos/" + file.getName(), "", safeDigest(file), 0, 0, 0, 0, "", Collections.emptyList()));
        }
        ensureDefault(result);
        return result;
    }

    /** Catalog metadata is additive: files created by older builds remain visible after a catalog exists. */
    private static void mergeScannedEntries(File root, List<WallpaperCandidate> result) {
        List<WallpaperCandidate> scanned = scan(root);
        for (WallpaperCandidate candidate : scanned) {
            boolean known = false;
            for (WallpaperCandidate existing : result) {
                if (existing.id.equals(candidate.id)
                        || (!existing.relativeFile.isEmpty() && existing.relativeFile.equals(candidate.relativeFile))) {
                    known = true;
                    break;
                }
            }
            if (!known) result.add(candidate);
        }
    }

    private static WallpaperCandidate image(String id, String title, String path, File root) {
        return new WallpaperCandidate(id, title, WallpaperCandidate.Type.IMAGE, path, "", safeDigest(resolve(root, path)), 0, 0, 0, 0, "", Collections.emptyList());
    }

    private static void ensureDefault(List<WallpaperCandidate> entries) {
        for (WallpaperCandidate candidate : entries) if (candidate.type == WallpaperCandidate.Type.DEFAULT) return;
        entries.add(WallpaperCandidate.defaultBackground());
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int count = input.read(bytes);
            return new String(bytes, 0, Math.max(0, count), StandardCharsets.UTF_8);
        }
    }

    private static String safeDigest(File file) {
        try { return file == null ? "" : digest(file, "SHA-256"); } catch (IOException ignored) { return ""; }
    }

    private static String digest(File file, String algorithm) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Missing media");
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (Exception failure) {
            throw new IOException("Digest failed", failure);
        }
    }

    private static String shortHash(File file) { return safeDigest(file).substring(0, Math.min(16, safeDigest(file).length())); }

    private static String relativePath(File root, File file) throws IOException {
        if (root == null || file == null) throw new IOException("Missing catalog path");
        String base = root.getCanonicalFile().getPath() + File.separator;
        String path = file.getCanonicalFile().getPath();
        if (!path.startsWith(base)) throw new IOException("Media escapes catalog root");
        return path.substring(base.length()).replace(File.separatorChar, '/');
    }
}
