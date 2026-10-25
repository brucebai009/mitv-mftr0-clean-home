package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Translucent glass material; the actual blur lives in the background layer. */
public final class GlassPanelDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density, radius;
    private final boolean modal;
    private int alpha = 255;
    private boolean focused, selected;
    private LinearGradient sheen, rim;

    public GlassPanelDrawable(float density, float radiusDp) { this(density, radiusDp, false); }
    public GlassPanelDrawable(float density, float radiusDp, boolean modal) {
        this.density = density; radius = radiusDp * density; this.modal = modal;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds.left + density, bounds.top + density, bounds.right - density, bounds.bottom - density);
        float bottom = Math.max(rect.top + 1f, rect.bottom);
        sheen = new LinearGradient(rect.left, rect.top, rect.right, bottom,
                new int[]{focused ? 0x57FFFFFF : 0x2BFFFFFF, 0x08FFFFFF, 0x14B4D9FF},
                new float[]{0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        rim = new LinearGradient(rect.left, rect.top, rect.right, bottom,
                focused ? 0xD9FFFFFF : 0x61FFFFFF,
                focused ? 0x669ACFFF : 0x1AFFFFFF, Shader.TileMode.CLAMP);
    }

    @Override public void draw(Canvas canvas) {
        if (rect.isEmpty()) return;
        paint.setStyle(Paint.Style.FILL); paint.setShader(null);
        paint.setColor(modal ? 0xFF293039 : selected ? 0x702B435C : 0x57111B28);
        paint.setAlpha(alpha * (modal ? 218 : selected ? 112 : 87) / 255);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setAlpha(alpha); paint.setShader(sheen); canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth((focused ? 1.7f : 0.8f) * density);
        paint.setShader(rim); canvas.drawRoundRect(rect, radius, radius, paint); paint.setShader(null);
    }

    @Override public boolean isStateful() { return true; }
    @Override protected boolean onStateChange(int[] states) {
        boolean nextFocus = false, nextSelected = false;
        for (int state : states) {
            if (state == android.R.attr.state_focused || state == android.R.attr.state_pressed) nextFocus = true;
            if (state == android.R.attr.state_selected || state == android.R.attr.state_checked) nextSelected = true;
        }
        if (focused == nextFocus && selected == nextSelected) return false;
        focused = nextFocus; selected = nextSelected; invalidateSelf(); return true;
    }
    @Override public void setAlpha(int value) { alpha = Math.max(0, Math.min(255, value)); invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @SuppressWarnings("deprecation") @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
