package com.gathin.tvhome;

import android.app.Activity;
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

/** Reproduces a saved/recreated RecyclerView viewport with its Dock focus restored at the top. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=30) @LooperMode(LooperMode.Mode.PAUSED)
public class SettingsReturnDockTest {
    public static class Home extends MainActivity {
        RecyclerView actualGrid;
        @Override public View getCurrentFocus() { return actualGrid == null ? null : actualGrid.findFocus(); }
        @Override public void onTileFocused(int position, View card) {
            ReflectionHelpers.callInstanceMethod(this, "parkDockOnlyRow");
        }
    }
    static void advance() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350)); }
    static final class Fixture {
        final Activity owner = Robolectric.buildActivity(Activity.class).setup().visible().get();
        final Home home = Robolectric.buildActivity(Home.class).get();
        final RecyclerView grid = new RecyclerView(owner);
        final GridLayoutManager lm = new GridLayoutManager(owner, 2) {
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent, View child,
                    android.graphics.Rect rect, boolean immediate, boolean focusedChildVisible) { return false; }
        };
        Fixture() {
            grid.setLayoutManager(lm); grid.setPadding(0,180,0,0); grid.setClipToPadding(false);
            AppAdapter adapter = new AppAdapter(owner, null, 2, com.gathin.tvhome.Config.load(owner)) {
                @Override public int getItemCount() { return 20; }
                @Override public int getItemViewType(int p) { return 0; }
                @Override public long getItemId(int p) { return p; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                    FrameLayout tile = new FrameLayout(owner);
                    tile.setLayoutParams(new RecyclerView.LayoutParams(300,80));
                    View card = new View(owner); card.setId(R.id.card); card.setFocusable(true);
                    tile.addView(card, new FrameLayout.LayoutParams(-1,-1));
                    return new RecyclerView.ViewHolder(tile) {};
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int p) {
                    h.itemView.findViewById(R.id.card).setOnFocusChangeListener((v, focused) -> {
                        if (focused) home.onTileFocused(p,v);
                    });
                }
            };
            grid.setAdapter(adapter); home.actualGrid = grid;
            ReflectionHelpers.setField(home,"grid",grid); ReflectionHelpers.setField(home,"adapter",adapter);
            ReflectionHelpers.setField(home,"dockRow",true); ReflectionHelpers.setField(home,"rowsBelowDock",0);
            ReflectionHelpers.setField(home,"COLUMNS",2); ReflectionHelpers.setField(home,"density",1f);
            FrameLayout root = new FrameLayout(owner); root.addView(grid); owner.setContentView(root);
            root.measure(View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY)); root.layout(0,0,640,360);
            advance();
            // State Android can restore after setting changes: Dock near top, full app rows exposed.
            lm.scrollToPositionWithOffset(0,-180); advance();
            root.setFocusableInTouchMode(true); root.requestFocus();
        }
        void request(int p) {
            ReflectionHelpers.callInstanceMethod(home,"requestGridFocus",
                    ReflectionHelpers.ClassParameter.from(int.class,p),
                    ReflectionHelpers.ClassParameter.from(int.class,R.id.card)); advance();
        }
        int top(int p) { return lm.getDecoratedTop(grid.findViewHolderForAdapterPosition(p).itemView); }
    }
    @Test public void newlyFocusedDockReturnsHomeWithoutExtraDownUp() {
        Fixture f = new Fixture(); assertEquals(0,f.top(0)); f.request(1);
        assertTrue(f.grid.findViewHolderForAdapterPosition(1).itemView.findViewById(R.id.card).isFocused());
        assertEquals(f.grid.getPaddingTop(),f.top(0));
    }
    @Test public void touchSettingsReturnAlsoRestoresDockGeometry() {
        Fixture f = new Fixture();
        Object vr = ReflectionHelpers.callInstanceMethod(f.grid,"getViewRootImpl");
        ReflectionHelpers.callInstanceMethod(vr,"ensureTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,true));
        f.request(1); assertEquals(f.grid.getPaddingTop(),f.top(0));
        assertFalse(f.grid.findFocus().isInTouchMode());
    }
    @Test public void lowerAppFocusDoesNotGetPulledBackToDock() {
        Fixture f = new Fixture(); f.request(2);
        assertTrue(f.grid.findViewHolderForAdapterPosition(2).itemView.findViewById(R.id.card).isFocused());
        assertEquals(Math.round(AppAdapter.TOP_ROOM_DP),f.top(2));
    }
}
