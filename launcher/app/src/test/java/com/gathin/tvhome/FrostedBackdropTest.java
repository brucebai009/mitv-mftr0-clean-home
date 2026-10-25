package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=30)
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class FrostedBackdropTest {
    ActivityController<Activity> lifecycle;
    Activity activity;
    FrameLayout root;
    final List<Runnable> work = new ArrayList<>();
    final List<FrostedBackdrop.Lease> leases = new ArrayList<>();
    @Before public void setup() {
        lifecycle = Robolectric.buildActivity(Activity.class).setup().visible(); activity = lifecycle.get();
        root = new FrameLayout(activity); activity.setContentView(root);
        root.addView(new View(activity) {
            final Paint paint = new Paint();
            { setWillNotDraw(false); }
            @Override protected void onDraw(Canvas canvas) {
                for (int x=0; x<1920; x+=5) {
                    paint.setColor((x/5)%2==0 ? Color.WHITE : Color.BLACK);
                    canvas.drawRect(x,0,x+5,1080,paint);
                }
            }
        }, new FrameLayout.LayoutParams(-1,-1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); root.layout(0,0,1920,1080);
    }
    @After public void cleanup() {
        for (FrostedBackdrop.Lease lease : leases) lease.close();
        for (Runnable job : work) job.run();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        lifecycle.pause().stop().destroy();
    }
    FrostedBackdrop.Lease open() {
        FrostedBackdrop.Lease lease = FrostedBackdrop.acquire(root, work::add); leases.add(lease); return lease;
    }
    void complete() { for (Runnable job : new ArrayList<>(work)) job.run(); work.clear(); Shadows.shadowOf(Looper.getMainLooper()).idle(); }
    @Test public void oneSnapshotBlursTextScaleDetailAcrossWholeWindowWithinMemoryCap() {
        open(); complete();
        ImageView image = root.findViewById(R.id.modalFrostedBackdrop);
        Bitmap frame = ((BitmapDrawable) image.getDrawable()).getBitmap();
        assertEquals(384,frame.getWidth()); assertEquals(216,frame.getHeight());
        int low=255,high=0;
        for (int x=64;x<320;x++) {
            int value=Color.red(frame.getPixel(x,100)); low=Math.min(low,value); high=Math.max(high,value);
        }
        assertTrue("High frequency background detail must be blurred", high-low < 16);
        assertTrue(low>40 && high<220); // Real blurred content, not a black fallback.
        assertEquals("FROSTED",FrostedBackdrop.lastResult);
    }
    @Test public void nestedDialogsShareOneLayerUntilBothLeasesClose() {
        int before=FrostedBackdrop.activeLayers;
        FrostedBackdrop.Lease a=open(),b=open();
        assertEquals(1,work.size()); assertEquals(before+1,FrostedBackdrop.activeLayers);
        a.close(); assertNotNull(root.findViewById(R.id.modalFrostedBackdrop));
        b.close(); assertNull(root.findViewById(R.id.modalFrostedBackdrop));
        assertEquals(before,FrostedBackdrop.activeLayers);
    }
    @Test public void rapidCloseReopenRejectsOldPixels() {
        FrostedBackdrop.Lease old=open(); old.close(); open();
        ImageView current=root.findViewById(R.id.modalFrostedBackdrop);
        work.get(0).run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(current.getDrawable());
        work.get(1).run(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(current.getDrawable() instanceof BitmapDrawable);
        assertSame(current,root.findViewById(R.id.modalFrostedBackdrop));
        work.clear();
    }
    @Test public void repeatedOpenCloseDoesNotAccumulateLayersOrStealFocus() {
        TextView control=new TextView(activity);control.setFocusableInTouchMode(true);root.addView(control);
        control.requestFocus(); int children=root.getChildCount(), layers=FrostedBackdrop.activeLayers;
        for(int i=0;i<20;i++) {
            FrostedBackdrop.Lease lease=open();
            View overlay=root.findViewById(R.id.modalFrostedBackdrop);
            assertFalse(overlay.isFocusable());assertFalse(overlay.isClickable());
            assertTrue(control.isFocused());lease.close();
            assertEquals(children,root.getChildCount());assertEquals(layers,FrostedBackdrop.activeLayers);
        }
        complete();assertNull(root.findViewById(R.id.modalFrostedBackdrop));
    }
    @Test public void unavailableWorkerUsesOpaqueBackdropAndStillCleansUp() {
        FrostedBackdrop.Lease lease=FrostedBackdrop.acquire(root, command -> {throw new java.util.concurrent.RejectedExecutionException();});
        leases.add(lease);
        ImageView image=root.findViewById(R.id.modalFrostedBackdrop);
        assertNotNull(image.getBackground());assertNull(image.getDrawable());
        assertEquals("OPAQUE_FALLBACK",FrostedBackdrop.lastResult);
        lease.close();assertNull(root.findViewById(R.id.modalFrostedBackdrop));
    }
    @Test public void dialogListenersKeepTheirOriginalLifecycleCallbacks() {
        int[] calls={0,0};FrostedDialog dialog=new FrostedDialog(activity);
        dialog.setContentView(new TextView(activity));
        dialog.setOnShowListener(d -> calls[0]++);dialog.setOnDismissListener(d -> calls[1]++);
        dialog.show();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,calls[0]);assertNotNull(activity.findViewById(R.id.modalFrostedBackdrop));
        dialog.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1,calls[1]);assertNull(activity.findViewById(R.id.modalFrostedBackdrop));
    }
    @Test public void commonSettingsChoiceDialogUsesTheSameBackdrop() {
        Dialog dialog=new GlassDialog.Builder(activity).setTitle("选择").setItems(new String[]{"一","二"},null).show();
        assertTrue(dialog instanceof FrostedDialog);
        assertNotNull(activity.findViewById(R.id.modalFrostedBackdrop));
        dialog.cancel();Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertNull(activity.findViewById(R.id.modalFrostedBackdrop));
    }
    @Test public void stoppingDialogRemovesBackdropEvenWithoutAnOnDismissCallback() {
        FrostedDialog dialog=new FrostedDialog(activity);dialog.setContentView(new TextView(activity));dialog.show();
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(dialog,"onStop");
        assertNull(activity.findViewById(R.id.modalFrostedBackdrop));
        dialog.dismiss();
    }
}
