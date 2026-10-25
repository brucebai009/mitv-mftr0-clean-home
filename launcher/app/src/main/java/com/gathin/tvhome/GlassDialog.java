package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small custom dialog for TVHome-owned choices; external system Activities stay external. */
public final class GlassDialog {
    private GlassDialog() { }
    public static final class Builder {
        private final Activity activity;
        private CharSequence title = "", message = "", positive, negative, neutral;
        private DialogInterface.OnClickListener onPositive, onNegative, onNeutral, onItem;
        private String[] items; private int checked = -1; private boolean singleChoice;
        public Builder(Activity activity) { this.activity = activity; }
        public Builder setTitle(int id) { return setTitle(activity.getText(id)); }
        public Builder setTitle(CharSequence value) { title = value; return this; }
        public Builder setMessage(int id) { return setMessage(activity.getText(id)); }
        public Builder setMessage(CharSequence value) { message = value; return this; }
        public Builder setPositiveButton(int id, DialogInterface.OnClickListener listener) { return setPositiveButton(activity.getText(id), listener); }
        public Builder setPositiveButton(CharSequence value, DialogInterface.OnClickListener listener) { positive = value; onPositive = listener; return this; }
        public Builder setNegativeButton(int id, DialogInterface.OnClickListener listener) { return setNegativeButton(activity.getText(id), listener); }
        public Builder setNegativeButton(CharSequence value, DialogInterface.OnClickListener listener) { negative = value; onNegative = listener; return this; }
        public Builder setNeutralButton(int id, DialogInterface.OnClickListener listener) { return setNeutralButton(activity.getText(id), listener); }
        public Builder setNeutralButton(CharSequence value, DialogInterface.OnClickListener listener) { neutral = value; onNeutral = listener; return this; }
        public Builder setItems(String[] values, DialogInterface.OnClickListener listener) { items = values.clone(); onItem = listener; return this; }
        public Builder setSingleChoiceItems(String[] values, int selected, DialogInterface.OnClickListener listener) { items = values.clone(); checked = selected; onItem = listener; singleChoice = true; return this; }
        public Dialog show() {
            Dialog dialog = new FrostedDialog(activity); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout card = new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24), dp(22), dp(24), dp(20));
            card.setBackground(new GlassPanelDrawable(activity.getResources().getDisplayMetrics().density, 24, true));
            if (title != null && title.length() != 0) card.addView(text(title, 26, false), new LinearLayout.LayoutParams(-1, -2));
            if (message != null && message.length() != 0) { TextView body = text(message, 18, false); body.setTextColor(0xE6FFFFFF); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(14); card.addView(body, lp); }
            View firstFocus = null;
            if (items != null && items.length != 0) {
                ScrollView scroll = new ScrollView(activity); LinearLayout list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, Math.min(items.length * dp(56), Math.max(dp(56), activity.getResources().getDisplayMetrics().heightPixels - dp(232)))); slp.topMargin = dp(16); card.addView(scroll, slp);
                for (int i = 0; i < items.length; i++) { final int index = i; TextView row = text((singleChoice && checked == i ? "✓  " : "    ") + items[i], 19, true); row.setSelected(singleChoice && checked == i); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), 0, dp(16), 0); row.setOnClickListener(v -> { if (onItem != null) onItem.onClick(dialog, index); if (!singleChoice) dialog.dismiss(); }); LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, dp(50)); rlp.bottomMargin = dp(6); list.addView(row, rlp); if (firstFocus == null || (singleChoice && checked == i)) firstFocus = row; }
            }
            LinearLayout actions = new LinearLayout(activity); actions.setGravity(Gravity.END); actions.setOrientation(LinearLayout.HORIZONTAL); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(48)); alp.topMargin = dp(20); if (neutral != null || negative != null || positive != null) card.addView(actions, alp);
            View neutralView = addAction(actions, neutral, onNeutral, dialog, DialogInterface.BUTTON_NEUTRAL); View negativeView = addAction(actions, negative, onNegative, dialog, DialogInterface.BUTTON_NEGATIVE); View positiveView = addAction(actions, positive, onPositive, dialog, DialogInterface.BUTTON_POSITIVE);
            if (firstFocus == null) firstFocus = negativeView != null ? negativeView : positiveView != null ? positiveView : neutralView;
            dialog.setContentView(card); dialog.setCanceledOnTouchOutside(false); final View focus = firstFocus; dialog.setOnShowListener(ignored -> { if (focus != null) focus.requestFocus(); });
            Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); WindowManager.LayoutParams attrs = window.getAttributes(); attrs.dimAmount = 0.22f; attrs.gravity = Gravity.CENTER; window.setAttributes(attrs); }
            dialog.show(); if (window != null) window.setLayout(Math.min(dp(640), activity.getResources().getDisplayMetrics().widthPixels - dp(96)), -2); return dialog;
        }
        private View addAction(LinearLayout parent, CharSequence label, DialogInterface.OnClickListener listener, Dialog dialog, int which) { if (label == null) return null; TextView button = text(label, 17, true); button.setGravity(Gravity.CENTER); button.setPadding(dp(12), 0, dp(12), 0); button.setOnClickListener(v -> { if (listener != null) listener.onClick(dialog, which); dialog.dismiss(); }); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f); lp.setMarginStart(dp(6)); parent.addView(button, lp); return button; }
        private TextView text(CharSequence value, int sp, boolean interactive) { TextView view = new TextView(activity); view.setText(value); view.setTextSize(sp); view.setTextColor(Color.WHITE); view.setFocusable(interactive); view.setClickable(interactive); if (interactive) view.setBackground(new GlassPanelDrawable(activity.getResources().getDisplayMetrics().density, 14)); return view; }
        private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    }
}
