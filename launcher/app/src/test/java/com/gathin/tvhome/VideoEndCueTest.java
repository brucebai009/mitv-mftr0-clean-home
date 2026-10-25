package com.gathin.tvhome;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class VideoEndCueTest {
    @Test public void capturesOnceNearTailWithoutAdvancingPlayback() {
        VideoEndCue cue = new VideoEndCue(new Handler(Looper.getMainLooper()));
        long end = SystemClock.elapsedRealtime() + 2000; int[] snapshots = {0};
        cue.start(() -> end - SystemClock.elapsedRealtime(), () -> snapshots[0]++);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300)); assertEquals(0, snapshots[0]);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); assertEquals(1, snapshots[0]);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5)); assertEquals(1, snapshots[0]);
    }
    @Test public void cancellationStopsWorkAfterLeavingHome() {
        VideoEndCue cue = new VideoEndCue(new Handler(Looper.getMainLooper())); int[] captures = {0};
        cue.start(() -> 2000, () -> captures[0]++); cue.cancel();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10)); assertEquals(0, captures[0]);
    }
}
