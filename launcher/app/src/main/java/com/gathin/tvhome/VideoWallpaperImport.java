package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Imports and validates one local silent MP4 into TVHome's private external-files directory. */
final class VideoWallpaperImport {
    private static final int BUFFER = 32 * 1024;

    private VideoWallpaperImport() { }

    static File importVideo(Context context, Uri uri) throws IOException {
        if (uri == null) throw new IOException("No video selected");
        File root = Config.dir(context);
        if (root == null || (!root.exists() && !root.mkdirs()) || !root.isDirectory()) {
            throw new IOException("Video storage unavailable");
        }
        File directory = new File(root, "videos");
        if ((!directory.exists() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("Video collection unavailable");
        }

        ContentResolver resolver = context.getContentResolver();
        String sourceName = displayName(resolver, uri);
        if (!VideoWallpaperPolicy.safeSourceName(sourceName)) {
            String mime = resolver.getType(uri);
            if ("video/mp4".equalsIgnoreCase(mime)) sourceName = "selected-video.mp4";
        }
        if (!VideoWallpaperPolicy.safeSourceName(sourceName)) throw new IOException("Only a safe .mp4 name is accepted");

        File temporary = File.createTempFile(".video-", ".part", directory);
        try {
            String digest = copyAndHash(resolver, uri, temporary);
            VideoSourceInfo info = VideoSourceInfo.inspect(temporary);
            VideoWallpaperPolicy.Result result = VideoWallpaperPolicy.validate(sourceName, temporary.length(),
                    info.width, info.height, info.durationMs, info.fps, info.mime, info.sdr);
            if (result != VideoWallpaperPolicy.Result.VALID) throw new IOException("Video rejected: " + result);
            if (info.needsNativeSurface(temporary.length()) && !info.hasHardwareDecoder())
                throw new IOException("No hardware decoder supports this original video size and frame rate");
            File target = new File(directory, VideoWallpaperPolicy.storageName(sourceName, digest));
            if (target.isFile() && target.length() == temporary.length()) {
                // Equal length is not identity: a previously interrupted or corrupted import must
                // be replaced rather than silently accepted.
                if (digest.equalsIgnoreCase(sha256(target))) {
                    temporary.delete();
                    return target;
                }
                if (!target.delete()) throw new IOException("Could not replace invalid video");
            }
            if (target.isFile() && !target.delete()) throw new IOException("Could not replace existing video");
            if (!temporary.renameTo(target)) throw new IOException("Could not save video");
            // Read back the digest so a partial or altered selection cannot become active.
            if (!digest.equalsIgnoreCase(sha256(target))) {
                target.delete();
                throw new IOException("Video hash readback failed");
            }
            return target;
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 unavailable", impossible);
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
        byte[] buffer = new byte[BUFFER];
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("Could not read video");
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (target.getParentFile().getUsableSpace() < 512L * 1024 * 1024 + count)
                    throw new IOException("Keep at least 512 MiB of free storage");
                if (total > VideoWallpaperPolicy.MAX_BYTES) throw new IOException("Video file is too large");
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
            }
        }
        if (total == 0) throw new IOException("Video file is empty");
        return hex(digest.digest());
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String value = cursor.getString(0);
                if (value != null && !value.trim().isEmpty()) return value;
            }
        } catch (Exception ignored) { }
        return "selected-video.mp4";
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

}
