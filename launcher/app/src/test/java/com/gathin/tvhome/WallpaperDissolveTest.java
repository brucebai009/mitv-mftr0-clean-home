package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class WallpaperDissolveTest {
    @org.junit.Before public void normalSystemAnimationScale() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        org.robolectric.shadows.ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        org.robolectric.util.ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,
                "setDurationScale", org.robolectric.util.ReflectionHelpers.ClassParameter.from(float.class, 1f));
    }
    static void advance(long ms) {
        for (long elapsed = 0; elapsed < ms; elapsed += 16)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(Math.min(16, ms-elapsed)));
    }
    static final class Source implements WallpaperVideoSession {
        public void start() {} public void stop() {} public void close() {}
        public void captureFrame(Consumer<Bitmap> callback) { fail("Black fade must never capture a video frame"); }
    }
    static final class Fixture {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().visible().get();
        final View overlay = new View(activity);
        final Source a = new Source(), b = new Source();
        final WallpaperDissolve fade;
        int released;
        float alphaAtRelease;
        Fixture(boolean animations, boolean reduced) {
            FrameLayout root = new FrameLayout(activity);
            root.addView(overlay, new FrameLayout.LayoutParams(640,360)); activity.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)); root.layout(0,0,640,360);
            fade = new WallpaperDissolve(overlay, animations, reduced);
            fade.bind(a, "A"); advance(32);
        }
        void eos() { fade.hold(a, 123, "B", "NATURAL_EOS", SystemClock.elapsedRealtime(),
                () -> { released++; alphaAtRelease = overlay.getAlpha(); }); }
        void ready() { fade.fadeOut(a); advance(650); eos(); advance(80); fade.bind(b, "B"); }
    }
    @Test public void outgoingVideoDarkensBeforeEosButNeverReleasesEarly() {
        Fixture f = new Fixture(true, false); f.fade.fadeOut(f.a); advance(220);
        assertTrue(f.overlay.getAlpha() > 0f && f.overlay.getAlpha() < 1f);
        assertEquals(Color.BLACK, ((ColorDrawable) f.overlay.getBackground()).getColor());
        advance(600); assertEquals(1f, f.overlay.getAlpha(), 0f); assertEquals(0, f.released);
        f.eos(); advance(80); assertEquals(1, f.released); assertEquals(1f, f.alphaAtRelease, 0f);
        f.fade.close();
    }
    @Test public void nextVideoOnlyFadesInAfterItsOwnRealFirstFrame() {
        Fixture f = new Fixture(true, false); f.ready(); advance(1200);
        assertTrue(f.fade.holding()); assertEquals(1f, f.overlay.getAlpha(), 0f);
        f.fade.firstFrame(f.a); advance(500); assertEquals(1f, f.overlay.getAlpha(), 0f);
        f.fade.firstFrame(f.b); advance(220);
        assertTrue(f.overlay.getAlpha() > 0f && f.overlay.getAlpha() < 1f);
        advance(500); assertFalse(f.fade.holding()); assertEquals(View.GONE, f.overlay.getVisibility());
        f.fade.close();
    }
    @Test public void missingTailCueStillFadesOldLastFrameBeforeReleasing() {
        Fixture f = new Fixture(true, false); f.eos(); advance(200);
        assertEquals(0, f.released); assertTrue(f.overlay.getAlpha() > 0f && f.overlay.getAlpha() < 1f);
        advance(500); assertEquals(1, f.released); assertEquals(1f, f.alphaAtRelease, 0f); f.fade.close();
    }
    @Test public void pauseDuringFadeOutRestoresSameVideoThenRearmsItsTail() {
        Fixture f = new Fixture(true, false); f.fade.fadeOut(f.a); advance(200); f.fade.pause(); advance(1000);
        assertFalse(f.fade.holding()); assertEquals(0, f.released);
        f.fade.resume(); f.fade.firstFrame(f.a); f.fade.fadeOut(f.a); advance(650); f.eos(); advance(80);
        assertEquals(1, f.released); f.fade.close();
    }
    @Test public void pauseDuringFadeInStaysBlackAndWaitsForNewFirstFrameOnReturn() {
        Fixture f = new Fixture(true, false); f.ready(); f.fade.firstFrame(f.b); advance(160);
        f.fade.pause(); advance(1000); assertTrue(f.fade.holding()); assertEquals(1f, f.overlay.getAlpha(), 0f);
        f.fade.resume(); advance(600); assertTrue(f.fade.holding());
        f.fade.firstFrame(f.b); advance(600); assertFalse(f.fade.holding()); f.fade.close();
    }
    @Test public void closeReleasesContinuationExactlyOnceAndRejectsLateFrames() {
        Fixture f = new Fixture(true, false); f.eos(); advance(100); f.fade.close(); f.fade.close(); advance(1000);
        f.fade.bind(f.b, "B"); f.fade.firstFrame(f.b); advance(1000);
        assertEquals(1, f.released); assertEquals(View.GONE, f.overlay.getVisibility());
    }
    @Test public void disabledAnimationsStillKeepBlackOverDecoderGap() {
        Fixture f = new Fixture(false, false); f.ready(); advance(1000);
        assertTrue(f.fade.holding()); assertEquals(1f, f.alphaAtRelease, 0f);
        f.fade.firstFrame(f.b); advance(100); assertFalse(f.fade.holding()); f.fade.close();
    }
    @Test public void reducedMotionUsesShortFadeAndKeepsFirstFrameGate() {
        Fixture f = new Fixture(true, true); f.fade.fadeOut(f.a); advance(200);
        assertEquals(1f, f.overlay.getAlpha(), 0f); f.eos(); advance(80); f.fade.bind(f.b, "B");
        f.fade.firstFrame(f.b); advance(220); assertFalse(f.fade.holding()); f.fade.close();
    }
    @Test public void decoderFailureClearsBlackSoErrorPosterCanBeShown() {
        Fixture f = new Fixture(true, false); f.ready(); f.fade.abort(); advance(600);
        assertFalse(f.fade.holding()); assertEquals("ERROR_POSTER", WallpaperDissolve.lastStage);
        assertEquals(View.GONE, f.overlay.getVisibility()); f.fade.close();
    }
    @Test public void duplicateFirstFrameDoesNotRestartFade() {
        Fixture f = new Fixture(true, false); f.ready(); f.fade.firstFrame(f.b); advance(200);
        float at = f.overlay.getAlpha(); f.fade.firstFrame(f.b); advance(80);
        assertTrue(f.overlay.getAlpha() < at); advance(400); assertFalse(f.fade.holding()); f.fade.close();
    }
    @Test public void journalRetainsAllCompletedTransitionsForSlowObservers() throws Exception {
        for (int i = 0; i < 5; i++) {
            Fixture f = new Fixture(true, false); f.ready(); f.fade.firstFrame(f.b); advance(650); f.fade.close();
        }
        org.json.JSONArray rows = TransitionJournal.snapshot();
        for (int i = rows.length()-5; i < rows.length(); i++) {
            org.json.JSONObject row = rows.getJSONObject(i);
            assertEquals("NATURAL_EOS", row.getString("reason"));
            assertTrue(row.getLong("fadeOutAt") < row.getLong("eosAt"));
            assertTrue(row.getLong("releaseAt") >= row.getLong("blackAt"));
            assertTrue(row.getLong("firstFrameAt") >= row.getLong("releaseAt"));
            assertTrue(row.getLong("settledAt") > row.getLong("firstFrameAt"));
        }
    }
    @Test public void journalIsBoundedAndSnapshotsCannotMutateHistory() throws Exception {
        for (int i = 0; i < 70; i++) TransitionJournal.begin(i, "A", "B", "TEST", i, i, i, false);
        org.json.JSONArray copy = TransitionJournal.snapshot(); assertEquals(64, copy.length());
        copy.getJSONObject(0).put("from", "CHANGED");
        assertEquals("A", TransitionJournal.snapshot().getJSONObject(0).getString("from"));
    }
}
