package com.gathin.tvhome;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/** Read on an importer/media worker only. Does not decode or allocate a whole video. */
final class VideoSourceInfo {
    final int width, height, fps;
    final long durationMs;
    final String mime;
    final boolean sdr;
    final MediaFormat format;
    private VideoSourceInfo(MediaFormat format, int fps) {
        this.format = format;
        mime = format.getString(MediaFormat.KEY_MIME);
        width = format.getInteger(MediaFormat.KEY_WIDTH);
        height = format.getInteger(MediaFormat.KEY_HEIGHT);
        durationMs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) / 1000 : 0;
        this.fps = fps;
        int transfer = format.containsKey(MediaFormat.KEY_COLOR_TRANSFER) ? format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) : 0;
        sdr = transfer != MediaFormat.COLOR_TRANSFER_ST2084 && transfer != MediaFormat.COLOR_TRANSFER_HLG;
    }
    boolean needsNativeSurface(long bytes) { return VideoWallpaperPolicy.needsNativeSurface(width, height, fps, bytes); }
    static VideoSourceInfo inspect(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getPath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")) continue;
                float rate = 0;
                if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    try { rate = format.getInteger(MediaFormat.KEY_FRAME_RATE); }
                    catch (ClassCastException notInteger) { rate = format.getFloat(MediaFormat.KEY_FRAME_RATE); }
                }
                if (rate <= 0) {
                    extractor.selectTrack(i);
                    ArrayList<Long> stamps = new ArrayList<>();
                    for (int n = 0; n < 121 && extractor.getSampleTime() >= 0; n++) {
                        stamps.add(extractor.getSampleTime());
                        if (!extractor.advance()) break;
                    }
                    Collections.sort(stamps);
                    ArrayList<Long> intervals = new ArrayList<>();
                    for (int n = 1; n < stamps.size(); n++) if (stamps.get(n) > stamps.get(n-1)) intervals.add(stamps.get(n)-stamps.get(n-1));
                    Collections.sort(intervals);
                    if (!intervals.isEmpty()) rate = 1_000_000f / intervals.get(intervals.size()/2);
                }
                return new VideoSourceInfo(format, Math.round(rate));
            }
            throw new IOException("No video track");
        } catch (RuntimeException error) { throw new IOException("Unreadable MP4 metadata", error); }
        finally { extractor.release(); }
    }
    JSONArray hardwareDecoders() {
        JSONArray result = new JSONArray();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (codec.isEncoder()) continue;
            String name = codec.getName().toLowerCase(java.util.Locale.ROOT);
            boolean hardware = Build.VERSION.SDK_INT >= 29 ? codec.isHardwareAccelerated()
                    : !name.startsWith("omx.google.") && !name.startsWith("c2.android.") && !name.contains("ffmpeg");
            if (!hardware) continue;
            try {
                MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                if (video == null || !video.areSizeAndRateSupported(width, height, fps) || !caps.isFormatSupported(format)) continue;
                result.put(codec.getName());
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    boolean hasHardwareDecoder() { return hardwareDecoders().length() > 0; }
    boolean canPlayOriginal(android.content.Context context, File file) {
        return hasHardwareDecoder() || HardwarePlaybackProof.has(context, file);
    }
    java.util.List<String> hardwareCandidates() {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (codec.isEncoder() || Build.VERSION.SDK_INT >= 29 && !codec.isHardwareAccelerated()) continue;
            String name = codec.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains("ffmpeg")) continue;
            for (String type : codec.getSupportedTypes()) if (mime.equalsIgnoreCase(type)) { result.add(codec.getName()); break; }
        }
        return result;
    }
    JSONArray decoderDiagnostics() {
        JSONArray result = new JSONArray();
        for (MediaCodecInfo codec : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (codec.isEncoder()) continue;
            try {
                MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(mime);
                MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                JSONObject item = new JSONObject();
                item.put("name", codec.getName());
                item.put("hardware", hardwareCandidates().contains(codec.getName()));
                item.put("fullFormatSupported", caps.isFormatSupported(format));
                item.put("sourceFormat", format.toString());
                if (video != null) {
                    item.put("sizeSupported", video.isSizeSupported(width, height));
                    item.put("sizeAndRateSupported", video.areSizeAndRateSupported(width, height, fps));
                    item.put("bitrateRange", video.getBitrateRange().toString());
                    item.put("widthRange", video.getSupportedWidths().toString());
                    item.put("heightRange", video.getSupportedHeights().toString());
                    if (video.isSizeSupported(width, height)) item.put("fpsAtSourceSize", video.getSupportedFrameRatesFor(width, height).toString());
                }
                JSONArray profiles = new JSONArray();
                for (MediaCodecInfo.CodecProfileLevel profile : caps.profileLevels) {
                    JSONObject entry = new JSONObject(); entry.put("profile", profile.profile); entry.put("level", profile.level); profiles.put(entry);
                }
                item.put("profileLevels", profiles); result.put(item);
            } catch (IllegalArgumentException ignored) { }
            catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        return result;
    }
    JSONObject json() throws org.json.JSONException {
        JSONObject value = new JSONObject();
        value.put("width", width); value.put("height", height); value.put("fps", fps);
        value.put("durationMs", durationMs); value.put("mime", mime); value.put("sdr", sdr);
        value.put("hardwareDecoders", hardwareDecoders());
        return value;
    }
    JSONObject diagnosticJson() throws org.json.JSONException {
        JSONObject value = json(); value.put("decoderDiagnostics", decoderDiagnostics()); return value;
    }
}
