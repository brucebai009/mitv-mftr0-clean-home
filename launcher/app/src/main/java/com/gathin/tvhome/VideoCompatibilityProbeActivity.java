package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Opt-in bounded original-source test. A named hardware codec, a native surface and actual frame callbacks;
 * no software decoder, transcoding, selection changes, HOME mutation or package removal. */
public final class VideoCompatibilityProbeActivity extends Activity implements SurfaceHolder.Callback {
    static volatile String lastReport = "{\"status\":\"NOT_RUN\"}";
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private boolean launched;
    private SurfaceView surface;
    private TextView label;
    private String id, runId;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        id = getIntent().getStringExtra("wallpaper_id");
        runId = getIntent().getStringExtra("run_id");
        if (runId == null) runId = Long.toString(System.currentTimeMillis());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        surface = new SurfaceView(this); surface.setZOrderOnTop(false);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
        label = new TextView(this); label.setText("正在校验原始视频并验证硬件播放…\n按返回可取消，当前壁纸不会更改");
        label.setTextColor(Color.WHITE); label.setTextSize(18); label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(0xCC15191F);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, (int)(80 * getResources().getDisplayMetrics().density), Gravity.BOTTOM);
        root.addView(label, lp); setContentView(root);
        surface.getHolder().addCallback(this);
        JSONObject report = new JSONObject();
        try { report.put("status", "PREPARING"); report.put("id", id); report.put("runId", runId); }
        catch (Exception ignored) { }
        lastReport = report.toString();
    }
    @Override public void surfaceCreated(SurfaceHolder holder) {
        if (launched) return;
        launched = true;
        new Thread(() -> probe(holder.getSurface()), "TvHome-HardwareProbe").start();
    }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { cancelled = true; }
    @Override protected void onPause() { cancelled = true; super.onPause(); }
    @Override protected void onDestroy() {
        cancelled = true; surface.getHolder().removeCallback(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); super.onDestroy();
    }

    private void probe(Surface output) {
        JSONObject report = new JSONObject(); boolean success = false, owned = false;
        try {
            report.put("id", id); report.put("runId", runId); report.put("status", "HASHING"); publish(report);
            WallpaperCandidate candidate = null;
            for (WallpaperCandidate item : WallpaperCatalog.load(Config.dir(this))) if (item.id.equals(id)) candidate = item;
            if (candidate == null || !candidate.isDynamic()) throw new IllegalArgumentException("Unknown original video");
            File file = WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile);
            VideoSourceInfo info = VideoSourceInfo.inspect(file);
            report.put("source", info.diagnosticJson());
            VideoWallpaperPolicy.Result policy = VideoWallpaperPolicy.validate(file.getName(), file.length(), info.width,
                    info.height, info.durationMs, info.fps, info.mime, info.sdr);
            if (policy != VideoWallpaperPolicy.Result.VALID) {
                report.put("status", "POLICY_BLOCKED"); report.put("reason", policy.name()); return;
            }
            String hash = hash(file);
            if (!candidate.sha256.isEmpty() && !hash.equalsIgnoreCase(candidate.sha256)) {
                report.put("status", "HASH_MISMATCH"); return;
            }
            report.put("sha256", hash); report.put("bytes", file.length());
            for (int i = 0; i < 20 && !cancelled; i++) {
                if (BackgroundResourceCoordinator.INSTANCE.acquire(BackgroundResourceCoordinator.Owner.PREVIEW, this)) { owned = true; break; }
                Thread.sleep(50);
            }
            if (!owned) { report.put("status", cancelled ? "ABORTED" : "RESOURCE_BUSY"); return; }
            CountDownLatch sized = new CountDownLatch(1);
            main.post(() -> { if (!cancelled) surface.getHolder().setFixedSize(info.width, info.height); sized.countDown(); });
            if (!sized.await(2, TimeUnit.SECONDS) || cancelled || !output.isValid()) {
                report.put("status", "SURFACE_UNAVAILABLE"); return;
            }
            JSONArray attempts = new JSONArray(); report.put("attempts", attempts);
            java.util.List<String> candidates = info.hardwareCandidates();
            if (candidates.isEmpty()) { report.put("status", "NO_ADVERTISED_HARDWARE_CODEC"); return; }
            report.put("status", "DECODING"); publish(report);
            for (String codec : candidates.subList(0, Math.min(2, candidates.size()))) {
                if (cancelled) break;
                JSONObject attempt = decode(file, info, codec, output); attempts.put(attempt);
                if (attempt.optBoolean("pass", false)) {
                    success = HardwarePlaybackProof.remember(this, file, hash, codec);
                    report.put("status", success ? "HARDWARE_DECODE_PASS" : "PROOF_SAVE_FAILED");
                    report.put("advertisedCheckWasFalse", !info.hasHardwareDecoder());
                    report.put("note", "Short original-rate hardware decode only; full wallpaper cadence, loop and physical output require device verification");
                    break;
                }
            }
            if (!success) report.put("status", cancelled ? "ABORTED" : "DECODE_NOT_VERIFIED");
        } catch (Exception error) {
            try { report.put("status", cancelled ? "ABORTED" : "PROBE_ERROR"); report.put("error", error.toString()); } catch (Exception ignored) { }
        } finally {
            if (owned) BackgroundResourceCoordinator.INSTANCE.release(this);
            publish(report);
            final boolean passed = success;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setResult(passed ? RESULT_OK : RESULT_CANCELED, new Intent().putExtra("wallpaper_id", id));
                finish();
            });
        }
    }

    private JSONObject decode(File file, VideoSourceInfo info, String name, Surface output) throws Exception {
        JSONObject result = new JSONObject(); result.put("codec", name); result.put("pass", false);
        MediaExtractor extractor = new MediaExtractor(); MediaCodec codec = null;
        AtomicInteger rendered = new AtomicInteger();
        AtomicLong firstNs = new AtomicLong(), lastNs = new AtomicLong(), firstPts = new AtomicLong(), lastPts = new AtomicLong();
        int width = 0, height = 0, outputs = 0;
        try {
            extractor.setDataSource(file.getPath());
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) if (info.mime.equals(extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME))) { track = i; break; }
            if (track < 0) throw new IllegalArgumentException("Video track disappeared");
            extractor.selectTrack(track);
            codec = MediaCodec.createByCodecName(name);
            codec.setOnFrameRenderedListener((c, pts, ns) -> {
                if (cancelled) return;
                if (firstNs.compareAndSet(0, ns)) firstPts.set(pts);
                lastNs.set(ns); lastPts.set(pts); rendered.incrementAndGet();
            }, main);
            codec.configure(extractor.getTrackFormat(track), output, null, 0); codec.start();
            boolean inputEnded = false, outputEnded = false;
            long firstSample = extractor.getSampleTime(), renderBase = 0, firstOutput = 0;
            long deadline = SystemClock.elapsedRealtime() + 12000;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (!cancelled && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEnded) {
                    int input = codec.dequeueInputBuffer(1000);
                    if (input >= 0) {
                        ByteBuffer bytes = codec.getInputBuffer(input);
                        if (bytes == null) throw new IllegalStateException("Missing input buffer");
                        long pts = extractor.getSampleTime();
                        int size = pts < 0 || pts - firstSample > 5_000_000 ? -1 : extractor.readSampleData(bytes, 0);
                        if (size < 0) { codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true; }
                        else { codec.queueInputBuffer(input, 0, size, pts, 0); extractor.advance(); }
                    }
                }
                int index = outputEnded ? MediaCodec.INFO_TRY_AGAIN_LATER : codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat(); result.put("outputFormat", format.toString());
                    width = format.getInteger(MediaFormat.KEY_WIDTH); height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    if (format.containsKey("crop-left") && format.containsKey("crop-right")) width = format.getInteger("crop-right") - format.getInteger("crop-left") + 1;
                    if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) height = format.getInteger("crop-bottom") - format.getInteger("crop-top") + 1;
                } else if (index >= 0) {
                    boolean eos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (!eos || bufferInfo.size > 0) {
                        if (renderBase == 0) { renderBase = System.nanoTime() + 60_000_000L; firstOutput = bufferInfo.presentationTimeUs; }
                        long when = renderBase + Math.max(0, bufferInfo.presentationTimeUs - firstOutput) * 1000;
                        codec.releaseOutputBuffer(index, when); outputs++;
                    } else codec.releaseOutputBuffer(index, false);
                    if (eos) outputEnded = true;
                } else if (outputEnded) Thread.sleep(10);
                if (HardwareDecodeVerdict.pass(info.width, info.height, info.fps, width, height,
                        rendered.get(), lastNs.get() - firstNs.get(), lastPts.get() - firstPts.get())) {
                    result.put("pass", true); break;
                }
            }
        } catch (Exception failure) { result.put("error", failure.toString()); }
        finally {
            if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } try { codec.release(); } catch (Exception ignored) { } }
            extractor.release();
        }
        result.put("outputWidth", width); result.put("outputHeight", height); result.put("outputFrames", outputs);
        result.put("renderedFrames", rendered.get()); result.put("renderSpanNs", lastNs.get() - firstNs.get());
        result.put("mediaSpanUs", lastPts.get() - firstPts.get());
        return result;
    }

    private String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] bytes = new byte[1024 * 1024]; int size;
            while ((size = stream.read(bytes)) >= 0) { if (cancelled) throw new InterruptedException("Cancelled"); digest.update(bytes, 0, size); }
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }
    private void publish(JSONObject report) {
        lastReport = report.toString(); android.util.Log.i("TvHome.Probe", lastReport);
    }
}
