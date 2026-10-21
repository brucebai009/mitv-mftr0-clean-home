package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Imports one user-selected picture into the launcher's private wallpaper collection. */
final class WallpaperImport {
    static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final int COPY_BUFFER = 32 * 1024;

    private WallpaperImport() { }

    static boolean supportedExtension(String name) {
        String extension = extension(name);
        return "jpg".equals(extension) || "jpeg".equals(extension)
                || "png".equals(extension) || "webp".equals(extension);
    }

    static int normalizeDim(int value) {
        int clamped = Math.max(0, Math.min(70, value));
        return ((clamped + 5) / 10) * 10;
    }

    static String fileName(String sourceName, String digest) {
        String extension = extension(sourceName);
        if (extension == null || extension.isEmpty()) extension = "jpg";
        String safeDigest = digest == null ? "" : digest.replaceAll("[^0-9a-fA-F]", "").toLowerCase(Locale.ROOT);
        if (safeDigest.length() > 16) safeDigest = safeDigest.substring(0, 16);
        if (safeDigest.isEmpty()) safeDigest = "image";
        return "wallpaper-" + safeDigest + "." + extension;
    }

    static File importImage(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No image selected");
        File root = Config.dir(context);
        if (root == null || (!root.exists() && !root.mkdirs()) || !root.isDirectory()) {
            throw new IOException("Wallpaper storage unavailable");
        }
        File directory = new File(root, "wallpapers");
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Wallpaper collection unavailable");
        }

        ContentResolver resolver = context.getContentResolver();
        String sourceName = displayName(resolver, uri);
        if (!supportedExtension(sourceName)) {
            String mime = resolver.getType(uri);
            sourceName = nameFromMime(sourceName, mime);
        }
        if (!supportedExtension(sourceName)) throw new IOException("Unsupported image format");

        File temporary = File.createTempFile(".wallpaper-", ".part", directory);
        String digest;
        try {
            digest = copyAndHash(resolver, uri, temporary);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getPath(), bounds);
            if (!WallpaperSize.accepted(bounds.outWidth, bounds.outHeight)) {
                throw new IOException("Image dimensions exceed the TV limit");
            }
            File target = new File(directory, fileName(sourceName, digest));
            if (target.isFile() && target.length() == temporary.length()) {
                temporary.delete();
                return target;
            }
            if (!temporary.renameTo(target)) throw new IOException("Could not save image");
            return target;
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    private static String copyAndHash(ContentResolver resolver, Uri uri, File target) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
        }
        long total = 0;
        byte[] buffer = new byte[COPY_BUFFER];
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Could not read image");
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IOException("Image file is too large");
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
            }
        }
        if (total == 0) throw new IOException("Image file is empty");
        return hex(digest.digest());
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (Exception ignored) {
            // Some document providers do not implement metadata queries; MIME is enough below.
        }
        return "selected-image";
    }

    private static String nameFromMime(String name, String mime) {
        if (supportedExtension(name) || mime == null) return name;
        if ("image/jpeg".equalsIgnoreCase(mime)) return name + ".jpg";
        if ("image/png".equalsIgnoreCase(mime)) return name + ".png";
        if ("image/webp".equalsIgnoreCase(mime)) return name + ".webp";
        return name;
    }

    private static String extension(String name) {
        if (name == null) return null;
        String value = name.toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        return dot >= 0 && dot + 1 < value.length() ? value.substring(dot + 1) : null;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
