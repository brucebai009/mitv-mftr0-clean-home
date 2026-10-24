package com.gathin.tvhome;

import android.Manifest;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Explicit ADB transfer of this launcher's state only. Never copies recovery/accessibility state. */
public final class LauncherTransferProvider extends ContentProvider {
    private static final String[] PREFS = {Config.SETTINGS_NAME, "wallpaper_rotation", "last_opened"};
    private static final String[] PRIVATE_FILES = {Order.FILE_NAME, Cards.STORE_FILE};

    private Context checkedContext() {
        Context context = getContext();
        if (context == null || context.checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) throw new SecurityException("WRITE_SECURE_SETTINGS required");
        return context;
    }

    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        Context context = checkedContext();
        try {
            if ("/export".equals(uri.getPath()) && "r".equals(mode)) {
                if (!TransferPolicy.SOURCE.equals(context.getPackageName())) throw new IOException("Export from original only");
                File snapshot = new File(context.getCacheDir(), "launcher-transfer-export.json");
                byte[] data = exportState(context).toString().getBytes(StandardCharsets.UTF_8);
                if (data.length > TransferPolicy.MAX_METADATA) throw new IOException("Metadata too large");
                atomicWrite(snapshot, data);
                return ParcelFileDescriptor.open(snapshot, ParcelFileDescriptor.MODE_READ_ONLY);
            }
            if ("/import".equals(uri.getPath()) && "w".equals(mode)) {
                if (!TransferPolicy.TARGET.equals(context.getPackageName())) throw new IOException("Import into compatibility ID only");
                return ParcelFileDescriptor.open(new File(context.getCacheDir(), "launcher-transfer-import.json"),
                        ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
            }
            throw new IOException("Unsupported transfer endpoint or mode");
        } catch (Exception failure) {
            throw new java.io.FileNotFoundException(failure.toString());
        }
    }

    private JSONObject exportState(Context context) throws Exception {
        File root = Config.dir(context);
        if (root == null) throw new IOException("External files unavailable");
        JSONObject data = new JSONObject().put("schema", 1).put("source", context.getPackageName())
                .put("target", TransferPolicy.TARGET).put("sourceRoot", root.getCanonicalPath());
        JSONObject prefs = new JSONObject();
        for (String name : PREFS) {
            JSONObject values = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().entrySet()) {
                Object value = entry.getValue(); String type;
                if (value instanceof Boolean) type = "boolean";
                else if (value instanceof Integer) type = "int";
                else if (value instanceof Long) type = "long";
                else if (value instanceof Float) type = "float";
                else if (value instanceof String) type = "string";
                else if (value instanceof java.util.Set) { type = "set"; value = new JSONArray((java.util.Set<?>) value); }
                else throw new IOException("Unsupported preference type");
                values.put(entry.getKey(), new JSONObject().put("type", type).put("value", value));
            }
            prefs.put(name, values);
        }
        data.put("preferences", prefs);
        JSONObject privateFiles = new JSONObject();
        for (String name : PRIVATE_FILES) {
            File file = new File(context.getFilesDir(), name);
            if (file.isFile()) {
                if (file.length() > 128 * 1024) throw new IOException("Private state too large");
                privateFiles.put(name, Base64.encodeToString(Files.readAllBytes(file.toPath()), Base64.NO_WRAP));
            }
        }
        data.put("privateFiles", privateFiles);
        List<File> files = new ArrayList<>();
        File[] children = root.listFiles();
        if (children == null) throw new IOException("Cannot enumerate source files");
        Arrays.sort(children);
        for (File child : children) {
            if (child.isFile() && ThemeFiles.path(Arrays.asList(child.getName())) != null) files.add(child);
            if (child.isDirectory() && ("videos".equals(child.getName()) || "wallpapers".equals(child.getName()))) {
                File[] collection = child.listFiles();
                if (collection == null) throw new IOException("Cannot enumerate media");
                Arrays.sort(collection);
                for (File item : collection) {
                    if (item.isFile() && ThemeFiles.path(Arrays.asList(child.getName(), item.getName())) != null) files.add(item);
                }
            }
        }
        if (files.size() > TransferPolicy.MAX_FILES) throw new IOException("Too many media files");
        JSONArray media = new JSONArray(); long total = 0;
        for (File file : files) {
            String path = file.getCanonicalPath();
            if (!path.startsWith(root.getCanonicalPath() + "/")) throw new IOException("File outside source root");
            if (file.length() > TransferPolicy.MAX_FILE_BYTES) throw new IOException("Media file too large");
            total += file.length();
            if (total > TransferPolicy.MAX_TOTAL_BYTES) throw new IOException("Media total too large");
            media.put(new JSONObject().put("path", path.substring(root.getCanonicalPath().length() + 1))
                    .put("size", file.length()).put("sha256", sha256(file)));
        }
        return data.put("media", media);
    }

    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        Context context = checkedContext();
        if (!TransferPolicy.TARGET.equals(context.getPackageName()))
            throw new IllegalArgumentException("Only compatibility transfer is supported");
        if ("prepare".equals(method)) {
            for (String name : PREFS) {
                if (!context.getSharedPreferences(name, Context.MODE_PRIVATE).getAll().isEmpty())
                    throw new IllegalStateException("Target already has preferences");
            }
            for (String name : PRIVATE_FILES) {
                if (new File(context.getFilesDir(), name).exists()) throw new IllegalStateException("Target already has private state");
            }
            if (new File(context.getFilesDir(), "launcher-transfer-complete").exists())
                throw new IllegalStateException("Target already migrated");
            File root = Config.dir(context);
            File[] files = root == null ? null : root.listFiles();
            if (files == null) throw new IllegalStateException("Target storage unavailable");
            if (files.length != 0) {
                String expected = extras == null ? null : extras.getString("resume_sha256");
                try {
                    File previous = new File(context.getCacheDir(), "launcher-transfer-import.json");
                    if (expected == null || !expected.matches("[0-9a-f]{64}") || !previous.isFile()
                            || previous.length() > TransferPolicy.MAX_METADATA || !sha256(previous).equals(expected))
                        throw new IOException("Resume requires the exact previous metadata SHA256");
                    JSONObject old = new JSONObject(new String(Files.readAllBytes(previous.toPath()), StandardCharsets.UTF_8));
                    if (old.getInt("schema") != 1 || !TransferPolicy.SOURCE.equals(old.getString("source"))
                            || !TransferPolicy.TARGET.equals(old.getString("target"))) throw new IOException("Wrong previous identity");
                    JSONArray media = old.getJSONArray("media");
                    if (media.length() > TransferPolicy.MAX_FILES) throw new IOException("Too many previous media files");
                    HashSet<String> owned = new HashSet<>();
                    for (int i=0; i<media.length(); i++) {
                        String path = media.getJSONObject(i).getString("path");
                        if (ThemeFiles.path(Arrays.asList(path.split("/", -1))) == null || !owned.add(path))
                            throw new IOException("Unsafe previous media path");
                    }
                    TransferStaging.checkInventory(root, owned);
                } catch (Exception failure) {
                    throw new IllegalStateException("Resume refused: " + failure.getMessage(), failure);
                }
            }
            return accepted(false);
        }
        if (!"apply".equals(method)) throw new IllegalArgumentException("Unknown transfer operation");
        try {
            File source = new File(context.getCacheDir(), "launcher-transfer-import.json");
            if (!source.isFile() || source.length() > TransferPolicy.MAX_METADATA) throw new IOException("Missing or oversized import");
            String digest = sha256(source);
            File done = new File(context.getFilesDir(), "launcher-transfer-complete");
            if (done.exists()) {
                if (!new String(Files.readAllBytes(done.toPath()), StandardCharsets.UTF_8).equals(digest))
                    throw new IOException("An earlier, different migration already exists");
                return accepted(true);
            }
            JSONObject data = new JSONObject(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8));
            if (data.getInt("schema") != 1 || !TransferPolicy.SOURCE.equals(data.getString("source"))
                    || !TransferPolicy.TARGET.equals(data.getString("target"))) throw new IOException("Wrong schema or identity");
            File root = Config.dir(context);
            if (root == null) throw new IOException("External storage unavailable");
            JSONArray media = data.getJSONArray("media");
            if (media.length() > TransferPolicy.MAX_FILES) throw new IOException("Too many media files");
            HashSet<String> paths = new HashSet<>(); long total = 0;
            for (int i = 0; i < media.length(); i++) {
                JSONObject entry = media.getJSONObject(i); String path = entry.getString("path");
                if (ThemeFiles.path(Arrays.asList(path.split("/", -1))) == null || !paths.add(path))
                    throw new IOException("Unsafe or duplicate media path");
                File file = new File(root, path);
                if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + "/") || !file.isFile()
                        || file.length() != entry.getLong("size") || file.length() > TransferPolicy.MAX_FILE_BYTES
                        || !sha256(file).equals(entry.getString("sha256"))) throw new IOException("Media hash/size mismatch: " + path
                                + " expectedSize=" + entry.getLong("size") + " actualSize=" + file.length()
                                + " expectedSha256=" + entry.getString("sha256")
                                + " actualSha256=" + (file.isFile() ? sha256(file) : "MISSING"));
                total += file.length(); if (total > TransferPolicy.MAX_TOTAL_BYTES) throw new IOException("Media total too large");
            }
            JSONObject prefs = data.getJSONObject("preferences");
            if (prefs.length() != PREFS.length) throw new IOException("Unexpected preference stores");
            List<SharedPreferences.Editor> editors = new ArrayList<>();
            for (String name : PREFS) {
                SharedPreferences store = context.getSharedPreferences(name, Context.MODE_PRIVATE);
                if (!store.getAll().isEmpty()) throw new IOException("Target already has preferences; do not overwrite");
                SharedPreferences.Editor editor = store.edit(); JSONObject values = prefs.getJSONObject(name);
                if (values.length() > 2048) throw new IOException("Too many preference keys");
                java.util.Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); JSONObject entry = values.getJSONObject(key); String type = entry.getString("type");
                    switch (type) {
                        case "boolean": editor.putBoolean(key, entry.getBoolean("value")); break;
                        case "int": editor.putInt(key, entry.getInt("value")); break;
                        case "long": editor.putLong(key, entry.getLong("value")); break;
                        case "float": editor.putFloat(key, (float) entry.getDouble("value")); break;
                        case "string":
                            String value = entry.getString("value");
                            if (Config.SETTINGS_NAME.equals(name) && "video_wallpaper_path".equals(key) && !value.isEmpty()) {
                                String relative = TransferPolicy.relativeVideo(value, data.getString("sourceRoot"));
                                if (!paths.contains(relative)) throw new IOException("Selected video was not transferred");
                                value = new File(root, relative).getAbsolutePath();
                            }
                            editor.putString(key, value); break;
                        case "set":
                            JSONArray valuesArray = entry.getJSONArray("value"); HashSet<String> set = new HashSet<>();
                            for (int j = 0; j < valuesArray.length(); j++) set.add(valuesArray.getString(j));
                            editor.putStringSet(key, set); break;
                        default: throw new IOException("Unsupported preference type");
                    }
                }
                // Copying files changes their mtime. Keeping the source theme signature would make
                // Config.load treat this migration as a new theme and erase columns/rows/wallpaper.
                if (Config.SETTINGS_NAME.equals(name)) {
                    editor.putString(Config.THEME_SIGNATURE_KEY, Config.themeSignature(root));
                }
                editors.add(editor);
            }
            JSONObject privateFiles = data.getJSONObject("privateFiles");
            for (java.util.Iterator<String> it = privateFiles.keys(); it.hasNext();) {
                if (!Arrays.asList(PRIVATE_FILES).contains(it.next())) throw new IOException("Unexpected private filename");
            }
            List<File> created = new ArrayList<>(); List<byte[]> contents = new ArrayList<>();
            for (String name : PRIVATE_FILES) {
                File destination = new File(context.getFilesDir(), name);
                if (destination.exists()) throw new IOException("Target already has private state");
                if (privateFiles.has(name)) {
                    byte[] bytes = Base64.decode(privateFiles.getString(name), Base64.NO_WRAP);
                    if (bytes.length > 128 * 1024) throw new IOException("Private state too large");
                    if (Order.FILE_NAME.equals(name) && Order.decode(new String(bytes, StandardCharsets.UTF_8)) == null)
                        throw new IOException("Invalid saved app order");
                    created.add(destination); contents.add(bytes);
                }
            }
            // Everything has validated before the first state mutation. Media was staged and hashed first.
            try {
                for (int i = 0; i < created.size(); i++) atomicWrite(created.get(i), contents.get(i));
                for (SharedPreferences.Editor editor : editors) if (!editor.commit()) throw new IOException("Preference commit failed");
                atomicWrite(done, digest.getBytes(StandardCharsets.UTF_8));
            } catch (Exception failure) {
                for (String name : PREFS) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
                for (File file : created) new AtomicFile(file).delete();
                throw failure;
            }
            return accepted(false);
        } catch (Exception failure) { throw new IllegalStateException("Transfer refused: " + failure.getMessage(), failure); }
    }

    private static Bundle accepted(boolean already) { Bundle result = new Bundle(); result.putBoolean("accepted", true); result.putBoolean("alreadyApplied", already); return result; }
    private static void atomicWrite(File file, byte[] data) throws IOException {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream output = null;
        try { output = atomic.startWrite(); output.write(data); atomic.finishWrite(output); }
        catch (IOException failure) { atomic.failWrite(output); throw failure; }
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[64 * 1024];
        try (FileInputStream input = new FileInputStream(file)) { int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
        StringBuilder text = new StringBuilder(); for (byte b : digest.digest()) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); return text.toString();
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
