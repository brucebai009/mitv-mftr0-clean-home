package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "w960dp-h540dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
public class DockScrollTransitionTest {
    private ActivityController<Activity> lifecycle;
    private RecyclerView grid;
    private DockScrollTransition transition;
    private FrameLayout root;

    @Before public void createRealRecyclerView() {
        lifecycle = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = lifecycle.get();
        root = new FrameLayout(activity); root.setFocusableInTouchMode(true);
        grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, 6) {
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent, View child,
                    Rect rect, boolean immediate, boolean focusedChildVisible) { return false; }
        });
        grid.setPadding(0, 400, 0, 0); grid.setClipToPadding(false); grid.setItemAnimator(null);
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                TextView view = new TextView(activity); view.setFocusable(true); view.setFocusableInTouchMode(true);
                view.setLayoutParams(new RecyclerView.LayoutParams(-1, 120));
                return new RecyclerView.ViewHolder(view) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}
            @Override public int getItemCount() { return 36; }
        });
        root.addView(grid, new FrameLayout.LayoutParams(-1, -1)); activity.setContentView(root);
        frame(32);
        root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 960, 600);
        frame(32);
        transition = new DockScrollTransition(grid, root);
    }
    @After public void close() { transition.cancel(); lifecycle.pause().stop().destroy(); }
    private void frame(int ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
    private boolean focus(int position) {
        RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(position);
        return h != null && h.itemView.requestFocus();
    }
    private void runToEnd() { for (int i=0; i<50 && transition.running(); i++) frame(16); }

    @Test public void downAndUpFocusArriveBeforeOrWithAnimationEnd() {
        AtomicInteger recovery = new AtomicInteger();
        transition.start(6, 16, () -> focus(6), recovery::incrementAndGet);
        runToEnd();
        assertFalse(transition.running()); assertEquals(0, recovery.get());
        assertEquals("SETTLED", DockScrollTransition.lastResult);
        assertTrue(grid.findViewHolderForAdapterPosition(6).itemView.isFocused());
        assertTrue(DockScrollTransition.lastFocusDelayMs <= 32);
        assertTrue(DockScrollTransition.lastDurationMs < 700);
        transition.start(0, grid.getPaddingTop(), () -> focus(0), recovery::incrementAndGet);
        runToEnd();
        assertEquals(0, recovery.get()); assertEquals("SETTLED", DockScrollTransition.lastResult);
        assertTrue(grid.findViewHolderForAdapterPosition(0).itemView.isFocused());
        assertEquals(0f, grid.getTranslationY(), 0f);
    }
    @Test public void legalContentEdgeDoesNotWaitForImpossiblePixelAnchor() {
        AtomicInteger recovery = new AtomicInteger();
        transition.start(35, -2000, () -> focus(35), recovery::incrementAndGet);
        // A detached target may require seeking first, but must not fall into a geometric 1.2s wait.
        for (int i=0; i<74 && transition.running(); i++) frame(16);
        assertEquals(0, recovery.get()); assertEquals("SETTLED", DockScrollTransition.lastResult);
        assertTrue(DockScrollTransition.lastAnchorClamped);
        assertTrue(DockScrollTransition.lastFocusDelayMs <= 32);
    }
    @Test public void cancelClearsTranslationAndNeverFiresStaleFocusOrRecovery() {
        AtomicInteger focus = new AtomicInteger(), recovery = new AtomicInteger();
        transition.start(6, 16, () -> { focus.incrementAndGet(); return focus(6); }, recovery::incrementAndGet);
        frame(32); int deliveredBeforeCancel = focus.get(); transition.cancel(); frame(1500);
        assertFalse(transition.running()); assertEquals(deliveredBeforeCancel, focus.get()); assertEquals(0, recovery.get());
        assertEquals(0f, grid.getTranslationY(), 0f);
        assertNotEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, grid.getDescendantFocusability());
    }
    @Test public void cancelBeforeQueuedLandingNeverHandsFocusOver() {
        AtomicInteger calls = new AtomicInteger();
        transition.start(6, 16, () -> { calls.incrementAndGet(); return focus(6); }, calls::incrementAndGet);
        transition.cancel(); frame(1500);
        assertEquals(0, calls.get()); assertFalse(transition.running());
        assertEquals("CANCELLED", DockScrollTransition.lastResult);
    }
    @Test public void systemAnimationsOffStillHandsFocusOverWithoutTimeout() {
        org.robolectric.util.ReflectionHelpers.callStaticMethod(android.animation.ValueAnimator.class,
                "setDurationScale", org.robolectric.util.ReflectionHelpers.ClassParameter.from(float.class, 0f));
        AtomicInteger recovery = new AtomicInteger();
        transition.start(6, 16, () -> focus(6), recovery::incrementAndGet);
        runToEnd();
        assertEquals(0, recovery.get()); assertEquals("SETTLED", DockScrollTransition.lastResult);
        assertTrue(DockScrollTransition.lastDurationMs < 150);
        assertTrue(DockScrollTransition.lastFocusDelayMs <= 32);
    }
    @Test public void removedTargetUsesBoundedRecoveryInsteadOfLeavingInputLocked() {
        AtomicInteger recovery = new AtomicInteger();
        transition.start(6, 16, () -> false, recovery::incrementAndGet);
        runToEnd();
        assertFalse(transition.running()); assertEquals(1, recovery.get());
        assertEquals("FOCUS_TARGET_UNAVAILABLE", DockScrollTransition.lastResult);
        assertTrue(DockScrollTransition.lastDurationMs < 700);
    }
}
