package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Remote-first editor. A local draft is committed only by Save; Back leaves the current dock intact. */
public final class DockEditorActivity extends Activity {
    private final Map<String, ResolveInfo> apps = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private List<String> selected;
    private LinearLayout pins, catalog;
    private TextView count;
    private int columns;
    private float density;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        Config config = Config.load(this);
        DeviceProfile profile = DeviceProfile.load(this);
        columns = config.columns;
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo app : getPackageManager().queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                if (app.activityInfo == null || !app.activityInfo.exported || !app.activityInfo.enabled) continue;
                String pkg = app.activityInfo.packageName;
                if (profile.suppressAospSettingsTile() && pkg.equals("com.android.tv.settings")) continue;
                if (LauncherAppVisibility.hidden(pkg, getPackageName()) || apps.containsKey(pkg)) continue;
                apps.put(pkg, app);
                labels.put(pkg, AppDisplayLabels.forPackage(pkg, Cards.cleanLabel(String.valueOf(app.loadLabel(getPackageManager())), pkg)));
            }
        }
        selected = state == null ? DockPreferences.load(this) : state.getStringArrayList("draft");
        if (selected == null) {
            // Seed from the actual current first row, including its previously saved order.
            AppAdapter current = new AppAdapter(this, null, columns, config);
            current.setSavedOrder(Order.ranks(Order.load(Order.file(this)), config.dockApps));
            current.reload(Collections.emptyList());
            selected = current.dockPackages();
        }
        selected = new ArrayList<>(selected);
        LinearLayout root = column();
        root.setPadding(dp(32), dp(22), dp(32), dp(22));
        root.setBackgroundColor(0xFF10151E);
        TextView title = text(getString(R.string.dock_editor_title), 28, Color.WHITE);
        root.addView(title);
        count = text("", 14, 0xB8FFFFFF);
        count.setPadding(0, dp(8), 0, dp(14));
        root.addView(count);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(section(getString(R.string.dock_editor_selected), true),
                new LinearLayout.LayoutParams(0, -1, 1.1f));
        View gap = new View(this); body.addView(gap, new LinearLayout.LayoutParams(dp(18), 1));
        body.addView(section(getString(R.string.dock_editor_available), false),
                new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END); footer.setPadding(0, dp(16), 0, 0);
        TextView cancel = button(getString(R.string.dock_editor_cancel), v -> finish());
        TextView save = button(getString(R.string.dock_editor_save), v -> {
            if (!DockPreferences.save(this, selected)) {
                Toast.makeText(this, R.string.library_save_failed, Toast.LENGTH_LONG).show(); return;
            }
            setResult(RESULT_OK); finish();
        });
        save.setId(R.id.dockSaveAction);
        footer.addView(cancel, new LinearLayout.LayoutParams(dp(120), dp(46)));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(dp(160), dp(46));
        saveLp.setMarginStart(dp(12)); footer.addView(save, saveLp);
        root.addView(footer);
        setContentView(root);
        render(null);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putStringArrayList("draft", new ArrayList<>(selected)); super.onSaveInstanceState(state);
    }

    private LinearLayout section(String heading, boolean pinned) {
        LinearLayout shell = column(); shell.setPadding(dp(16), dp(10), dp(16), dp(12));
        shell.setBackground(new GlassPanelDrawable(density, 22));
        TextView label = text(heading, 19, Color.WHITE); label.setPadding(0, dp(8), 0, dp(12)); shell.addView(label);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false);
        scroll.setId(pinned ? R.id.dockPinnedScroll : R.id.dockAvailableScroll);
        LinearLayout items = column(); scroll.addView(items);
        if (pinned) pins = items; else catalog = items;
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); return shell;
    }

    private void render(String focusTag) {
        pins.removeAllViews(); catalog.removeAllViews();
        count.setText(getString(R.string.dock_editor_hint, selected.size(), columns));
        if (selected.isEmpty()) {
            TextView empty = text(getString(R.string.dock_editor_empty), 16, 0xB8FFFFFF);
            empty.setPadding(dp(8), dp(14), dp(8), dp(14)); pins.addView(empty);
        }
        for (int i = 0; i < selected.size(); i++) {
            String pkg = selected.get(i); final int index = i;
            LinearLayout row = appRow(pkg, (i + 1) + "  ");
            TextView up = button("↑", v -> {
                selected = DockSelection.move(selected, index, index - 1); render(pkg + ":up");
            });
            up.setTag(pkg + ":up"); up.setContentDescription(getString(R.string.dock_editor_move_up, label(pkg)));
            TextView down = button("↓", v -> {
                selected = DockSelection.move(selected, index, index + 1); render(pkg + ":down");
            });
            down.setTag(pkg + ":down"); down.setContentDescription(getString(R.string.dock_editor_move_down, label(pkg)));
            TextView remove = button("−", v -> {
                selected.remove(pkg); render(pkg + ":add");
            });
            remove.setTag(pkg + ":remove"); remove.setContentDescription(getString(R.string.dock_editor_remove, label(pkg)));
            for (TextView action : new TextView[]{up, down, remove}) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(38));
                lp.setMarginStart(dp(5)); row.addView(action, lp);
            }
            addRow(pins, row);
        }
        List<String> available = new ArrayList<>(apps.keySet());
        Collator collator = Collator.getInstance(); available.sort((a, b) -> collator.compare(label(a), label(b)));
        for (String pkg : available) {
            LinearLayout row = appRow(pkg, "");
            boolean chosen = selected.contains(pkg);
            TextView state = text(chosen ? "✓" : "+", 22, chosen ? 0xFF8CE6C1 : Color.WHITE);
            state.setGravity(Gravity.CENTER); row.addView(state, new LinearLayout.LayoutParams(dp(38), dp(38)));
            row.setFocusable(true); row.setClickable(true); row.setTag(pkg + ":add");
            row.setContentDescription(label(pkg) + " · " + getString(chosen ? R.string.dock_editor_selected : R.string.dock_editor_add));
            row.setOnClickListener(v -> {
                if (selected.contains(pkg)) { selected.remove(pkg); render(pkg + ":add"); return; }
                if (selected.size() >= columns) {
                    Toast.makeText(this, R.string.dock_editor_full, Toast.LENGTH_SHORT).show(); return;
                }
                selected.add(pkg); render(pkg + ":add");
            });
            addRow(catalog, row);
        }
        View target = focusTag == null ? null : getWindow().getDecorView().findViewWithTag(focusTag);
        if (target != null) target.requestFocus();
        else if (catalog.getChildCount() > 0) catalog.getChildAt(0).requestFocus();
    }

    private LinearLayout appRow(String pkg, String prefix) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(10), dp(6)); row.setBackground(new GlassPanelDrawable(density, 14));
        ImageView icon = new ImageView(this);
        if (apps.containsKey(pkg)) try { icon.setImageDrawable(apps.get(pkg).loadIcon(getPackageManager())); }
        catch (RuntimeException ignored) { }
        row.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView name = text(prefix + label(pkg), 15, Color.WHITE);
        name.setPadding(dp(10), 0, dp(6), 0); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(44), 1)); name.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    private String label(String pkg) { return labels.containsKey(pkg) ? labels.get(pkg) : pkg; }
    private void addRow(LinearLayout parent, View row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58)); lp.bottomMargin = dp(8); parent.addView(row, lp);
    }
    private TextView button(String label, View.OnClickListener action) {
        TextView button = text(label, 17, Color.WHITE); button.setGravity(Gravity.CENTER);
        button.setFocusable(true); button.setClickable(true); button.setBackground(new GlassPanelDrawable(density, 12));
        button.setOnClickListener(action); return button;
    }
    private TextView text(String text, int size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private int dp(int value) { return Math.round(value * density); }
}
