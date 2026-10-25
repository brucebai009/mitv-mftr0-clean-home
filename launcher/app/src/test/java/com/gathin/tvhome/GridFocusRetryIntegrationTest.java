package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Runs MainActivity's actual posted runnable, including its identity and cancellation guards. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
@LooperMode(LooperMode.Mode.PAUSED)
public class GridFocusRetryIntegrationTest {
    static final class Target extends View {
        int attempts, deny;
        Target(Activity owner) { super(owner); setId(R.id.card); setFocusable(true); setFocusableInTouchMode(true); }
        @Override public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
            attempts++;
            if (attempts <= deny) return false;
            return super.requestFocus(direction, previouslyFocusedRect);
        }
    }
    static final class Fixture {
        final Activity owner = Robolectric.buildActivity(Activity.class).setup().visible().get();
        final MainActivity home = Robolectric.buildActivity(MainActivity.class).get();
        final RecyclerView grid = new RecyclerView(owner);
        final Target target = new Target(owner);
        Fixture() {
            grid.setLayoutManager(new GridLayoutManager(owner, 1));
            AppAdapter adapter = new AppAdapter(owner, null, 1, com.gathin.tvhome.Config.load(owner)) {
                @Override public int getItemCount() { return 1; }
                @Override public int getItemViewType(int position) { return 0; }
                @Override public long getItemId(int position) { return 1; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    target.setLayoutParams(new RecyclerView.LayoutParams(300, 100));
                    return new RecyclerView.ViewHolder(target) {};
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {}
            };
            grid.setAdapter(adapter);
            FrameLayout root = new FrameLayout(owner); root.addView(grid); owner.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY)); root.layout(0,0,640,360);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
            root.setFocusableInTouchMode(true); root.requestFocus(); target.attempts = 0;
            ReflectionHelpers.setField(home, "grid", grid); ReflectionHelpers.setField(home, "adapter", adapter);
            ReflectionHelpers.setField(home, "dockRow", false); ReflectionHelpers.setField(home, "density", 1f);
        }
        void request() {
            ReflectionHelpers.callInstanceMethod(home, "requestGridFocus",
                    ReflectionHelpers.ClassParameter.from(int.class, 0),
                    ReflectionHelpers.ClassParameter.from(int.class, R.id.card));
        }
        void advance() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400)); }
    }
    @Test public void transientFocusRefusalActuallyRetriesAndSucceeds() {
        Fixture f = new Fixture(); f.target.deny = 1; f.request(); f.advance();
        assertEquals(2, f.target.attempts); assertTrue(f.target.isFocused());
        assertNull(ReflectionHelpers.getField(f.home, "pendingFocusRequest"));
    }
    @Test public void permanentRefusalStopsAfterTwelveFrames() {
        Fixture f = new Fixture(); f.target.deny = 100; f.request(); f.advance();
        assertEquals(12, f.target.attempts); assertFalse(f.target.isFocused());
        assertNull(ReflectionHelpers.getField(f.home, "pendingFocusRequest"));
    }
    @Test public void pageExitCancelsTheQueuedFocusRequest() {
        Fixture f = new Fixture(); f.request();
        ReflectionHelpers.callInstanceMethod(f.home, "cancelPendingFocusScroll"); f.advance();
        assertEquals(0, f.target.attempts);
    }
    @Test public void returningFromTouchPageRestoresTheNonTouchFocusableTvCard() {
        Fixture f = new Fixture(); f.target.setFocusableInTouchMode(false);
        Object root = ReflectionHelpers.callInstanceMethod(f.grid, "getViewRootImpl");
        ReflectionHelpers.callInstanceMethod(root, "ensureTouchMode",
                ReflectionHelpers.ClassParameter.from(boolean.class, true));
        assertTrue(f.target.isInTouchMode()); f.target.attempts = 0;
        f.request(); f.advance();
        assertFalse(f.target.isInTouchMode()); assertTrue(f.target.isFocused());
        assertNull(ReflectionHelpers.getField(f.home, "pendingFocusRequest"));
    }
}
