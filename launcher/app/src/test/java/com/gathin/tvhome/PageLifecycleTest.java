package com.gathin.tvhome;

import android.os.Bundle;
import android.os.Looper;
import android.widget.FrameLayout;
import android.app.Activity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class PageLifecycleTest {
    @Test public void recyclerViewStateCanBeSavedWhenOpeningAnotherPage() {
        try (ActivityController<Activity> owner = Robolectric.buildActivity(Activity.class).setup()) {
            Activity activity = owner.get();
            RecyclerView grid = new RecyclerView(activity); grid.setId(1234);
            grid.setLayoutManager(new GridLayoutManager(activity, 6));
            FrameLayout root = new FrameLayout(activity); root.addView(grid); activity.setContentView(root);
            // RecyclerView.SavedState extends AbsSavedState: absent from the returned installed APK.
            owner.pause().saveInstanceState(new Bundle()).stop();
        }
    }
    @Test public void settingsAndDockEditorRemainAliveWithoutInput() {
        try (ActivityController<LauncherSettingsActivity> settings =
                     Robolectric.buildActivity(LauncherSettingsActivity.class).setup()) {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
            assertFalse(settings.get().isFinishing());
            assertNotNull(settings.get().findViewById(R.id.videoWallpaperChoice));
            try (ActivityController<DockEditorActivity> dock = Robolectric.buildActivity(DockEditorActivity.class).setup()) {
                settings.pause().saveInstanceState(new Bundle()).stop();
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
                assertFalse(dock.get().isFinishing());
                assertNotNull(dock.get().findViewById(R.id.dockSaveAction));
            }
        }
    }
}
