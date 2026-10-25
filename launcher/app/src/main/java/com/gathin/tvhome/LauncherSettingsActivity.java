package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.TextureView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private Dialog activePreview;
    private FrameLayout settingsRoot;
    private SettingsBackdropRenderer settingsBackdrop;
    private ImageView settingsBackdropView;
    private ScrollView settingsScroll;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperDimChoice;
    private TextView videoWallpaperChoice;
    private TextView wallpaperPlaylistSwitch;
    private TextView wallpaperPlaylistChoice;
    private TextView glassBlurChoice;
    private TextView reducedMotionChoice;
    private TextView staticCoverChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView homeHelperChoice;
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;
    private int glassBlurRadius = 6;
    private boolean reducedMotion;
    private boolean staticCover;
    private List<WallpaperCandidate> wallpaperCatalog = java.util.Collections.emptyList();
    private final ExecutorService wallpaperImporter = Executors.newSingleThreadExecutor();
    private static final int REQUEST_WALLPAPER = 1101;
    private static final int REQUEST_VIDEO_WALLPAPER = 1102;
    private static final int REQUEST_VIDEO_PROBE = 1103;

    @Override
    protected void onCreate(Bundle state) {
        long createStarted = android.os.SystemClock.elapsedRealtime();
        android.util.Log.i("TvHome.Settings", "create begin");
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        settingsRoot = findViewById(R.id.settingsRoot);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        settingsBackdropView = findViewById(R.id.settingsBackdrop);
        settingsBackdrop = new SettingsBackdropRenderer(settingsBackdropView);
        settingsScroll = findViewById(R.id.settingsContentScroll);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        glassBlurRadius = choices.getInt("glass_blur_radius", 6);
        reducedMotion = choices.getBoolean("reduced_motion", config.reducedMotion);
        staticCover = choices.getBoolean("static_cover", false);
        wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
        showSettingsBackdrop(config);
        applyGlassSurfaces();
        wireCategory(R.id.settingsCategoryAppearance, R.id.sectionAppearance);
        wireCategory(R.id.settingsCategoryApps, R.id.sectionApps);
        wireCategory(R.id.settingsCategoryMotion, R.id.sectionMotion);
        wireCategory(R.id.settingsCategorySource, R.id.sectionSource);
        wireCategory(R.id.settingsCategoryDesktop, R.id.sectionDesktop);
        wireCategory(R.id.settingsCategorySystem, R.id.sectionSystem);

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperDimChoice = findViewById(R.id.wallpaperDimChoice);
        wallpaperDimChoice.setOnClickListener(v -> showWallpaperDimPicker());
        videoWallpaperChoice = findViewById(R.id.videoWallpaperChoice);
        videoWallpaperChoice.setOnClickListener(v -> showWallpaperGallery());
        wallpaperPlaylistSwitch = findViewById(R.id.wallpaperPlaylistSwitch);
        wallpaperPlaylistSwitch.setOnClickListener(v -> toggleWallpaperPlaylist());
        wallpaperPlaylistChoice = findViewById(R.id.wallpaperPlaylistChoice);
        wallpaperPlaylistChoice.setOnClickListener(v -> showWallpaperPlaylistPicker());
        findViewById(R.id.followThemeChoice).setVisibility(View.GONE);
        glassBlurChoice = findViewById(R.id.glassBlurChoice);
        glassBlurChoice.setOnClickListener(v -> showGlassBlurPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new GlassDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.WALLPAPER_DIM_KEY).remove("background_mode")
                    .remove("video_wallpaper_path").remove("glass_blur_radius")
                    .remove("reduced_motion").remove("static_cover")
                    .remove(WallpaperPlaylistStore.ENABLED_KEY).remove(WallpaperPlaylistStore.IDS_KEY)
                    .remove(WallpaperPlaylistStore.CURRENT_KEY).remove(WallpaperPlaylistStore.PENDING_KEY)
                    .commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            glassBlurRadius = 6;
            reducedMotion = current.reducedMotion;
            staticCover = false;
            wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
            showSettingsBackdrop(current);
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        homeHelperChoice = findViewById(R.id.homeHelperChoice);
        homeHelperChoice.setOnClickListener(v -> {
            HomeReturnState.configure(this, true);
            if (!HomeReturnState.systemEnabled(this) || !HomeReturnService.isConnected()) {
                Toast.makeText(this, R.string.home_return_enable_help, Toast.LENGTH_LONG).show();
                open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
            refreshHomeHelper(homeHelperChoice);
        });
        findViewById(R.id.homeNormalBehavior).setOnClickListener(v -> {
            // Do not explicitly launch Xiaomi's permission-protected HOME Activity.
            HomeReturnState.setEnabled(this, false);
            boolean requested = HomeReturnService.openStockAfterPause();
            refreshHomeHelper(homeHelperChoice);
            if (!requested) Toast.makeText(this, R.string.home_return_paused_help, Toast.LENGTH_LONG).show();
        });
        refreshHomeHelper(homeHelperChoice);
        findViewById(R.id.homeSignalSourceChoice).setOnClickListener(v ->
                open(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_OPEN_INPUTS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        findViewById(R.id.homeScreensaverChoice).setOnClickListener(v ->
                showWallpaperIdlePicker());
        findViewById(R.id.dockEditorChoice).setOnClickListener(v ->
                startActivityForResult(new Intent(this, DockEditorActivity.class), MainActivity.DOCK_EDITOR_REQUEST));
        findViewById(R.id.homeAppsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        findViewById(R.id.homeWifiChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        reducedMotionChoice = findViewById(R.id.reducedMotionChoice);
        reducedMotionChoice.setOnClickListener(v -> {
            reducedMotion = !reducedMotion;
            choices.edit().putBoolean("reduced_motion", reducedMotion).apply();
            setResult(RESULT_OK); refresh();
        });
        staticCoverChoice = findViewById(R.id.staticCoverChoice);
        staticCoverChoice.setOnClickListener(v -> {
            staticCover = !staticCover;
            choices.edit().putBoolean("static_cover", staticCover).apply();
            setResult(RESULT_OK); refresh();
        });
        refresh();
        refreshHomeStatus((TextView) findViewById(R.id.defaultHomeChoice));
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
        android.util.Log.i("TvHome.Settings", "create end elapsedMs="
                + (android.os.SystemClock.elapsedRealtime() - createStarted)
                + " columns=" + columns + " rows=" + rowsBelowDock);
        settingsRoot.post(() -> android.util.Log.i("TvHome.Settings", "first UI queue pass"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (homeHelperChoice != null) refreshHomeHelper(homeHelperChoice);
        TextView home = findViewById(R.id.defaultHomeChoice);
        if (home != null) refreshHomeStatus(home);
    }

    @Override
    protected void onPause() {
        closePreview();
        super.onPause();
    }

    private void closePreview() {
        if (activePreview != null) activePreview.dismiss();
        activePreview = null;
    }

    private void refreshHomeHelper(TextView choice) {
        boolean active = HomeReturnState.enabled(this) && HomeReturnState.systemEnabled(this)
                && HomeReturnService.isConnected();
        choice.setText(active ? R.string.home_return_active : R.string.home_return_inactive);
        choice.setContentDescription(getString(R.string.home_return_description));
        ((TextView) findViewById(R.id.homeNormalBehavior)).setText(R.string.home_return_pause);
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showSettingsBackdrop(Config config) {
        if (settingsBackdrop == null) return;
        File source = config.wallpaper;
        if (source == null) {
            String path = choices.getString("video_wallpaper_path", "");
            WallpaperCandidate selected = findCandidate(choices.getString(Config.WALLPAPER_CHOICE_KEY, ""));
            if (selected != null && !selected.posterRelativeFile.isEmpty()) source = WallpaperCatalog.resolve(Config.dir(this), selected.posterRelativeFile);
            if (source == null && !path.isEmpty()) source = new File(path);
        }
        Bitmap bitmap = source == null ? null : decodeBackdrop(source);
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(config.backgroundColor);
        }
        settingsBackdrop.showOwnedSnapshot(bitmap, glassBlurRadius);
    }

    private Bitmap decodeBackdrop(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
            if (bitmap == null) return null;
            int w = Math.min(480, bitmap.getWidth()), h = Math.min(270, bitmap.getHeight());
            if (bitmap.getWidth() != w || bitmap.getHeight() != h) {
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, w, h, true); bitmap.recycle(); return scaled;
            }
            return bitmap;
        } catch (RuntimeException | OutOfMemoryError ignored) { return null; }
    }

    private void applyGlassSurfaces() {
        int[] ids = {R.id.wallpaperDimChoice, R.id.glassBlurChoice, R.id.videoWallpaperChoice,
                R.id.wallpaperPlaylistSwitch, R.id.wallpaperPlaylistChoice,
                R.id.followThemeChoice, R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7, R.id.rows0,
                R.id.rows1, R.id.rows2, R.id.rows3, R.id.dockEditorChoice, R.id.homeAppsChoice, R.id.animationsChoice, R.id.advancedChoice,
                R.id.reducedMotionChoice, R.id.staticCoverChoice, R.id.homeSignalSourceChoice, R.id.defaultHomeChoice,
                R.id.homeHelperChoice, R.id.homeNormalBehavior, R.id.homeScreensaverChoice, R.id.tvSettingsChoice,
                R.id.homeWifiChoice};
        for (int id : ids) { View view = findViewById(id); if (view != null) view.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14)); }
        int[] categories = {R.id.settingsCategoryAppearance, R.id.settingsCategoryApps, R.id.settingsCategoryMotion,
                R.id.settingsCategorySource, R.id.settingsCategoryDesktop, R.id.settingsCategorySystem};
        for (int id : categories) { View view = findViewById(id); if (view != null) view.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14)); }
    }

    private void wireCategory(int categoryId, int sectionId) {
        TextView category = findViewById(categoryId);
        View section = findViewById(sectionId);
        if (category == null || section == null) return;
        category.setOnClickListener(v -> settingsScroll.post(() -> {
            settingsScroll.smoothScrollTo(0, section.getTop());
            if (categoryId == R.id.settingsCategoryApps) findViewById(R.id.dockEditorChoice).requestFocus();
        }));
        if (categoryId == R.id.settingsCategoryApps) category.setNextFocusRightId(R.id.dockEditorChoice);
    }

    private void showWallpaperGallery() {
        wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
        Dialog dialog = new FrostedDialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24), dp(20), dp(24), dp(18));
        card.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 24, true));
        TextView title = new TextView(this); title.setText("动态壁纸"); title.setTextColor(Color.WHITE); title.setTextSize(26); card.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        for (WallpaperCandidate candidate : wallpaperCatalog) {
            if (!candidate.isDynamic()) continue;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true); row.setPadding(dp(10), dp(6), dp(14), dp(6)); row.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14));
            ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); Bitmap image = candidateThumbnail(candidate); if (image != null) thumb.setImageBitmap(image); row.addView(thumb, new LinearLayout.LayoutParams(dp(150), dp(84)));
            TextView label = new TextView(this); label.setText(candidate.title + (candidate.isDynamic() ? "\n动态 · 原始规格" : candidate.type == WallpaperCandidate.Type.DEFAULT ? "\n系统默认" : "\n图片")); label.setTextColor(Color.WHITE); label.setTextSize(18); label.setGravity(Gravity.CENTER_VERTICAL); label.setPadding(dp(16), 0, 0, 0); row.addView(label, new LinearLayout.LayoutParams(0, dp(84), 1f));
            row.setOnClickListener(v -> {
                row.setEnabled(false);
                label.setText(candidate.title + "\n正在校验原始视频…");
                wallpaperImporter.execute(() -> {
                    String failure = null;
                    boolean needsProbe = false;
                    try {
                        if (candidate.isDynamic()) {
                            File file = WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile);
                            if (file == null || !file.isFile()) throw new IOException("视频不存在");
                            VideoSourceInfo info = VideoSourceInfo.inspect(file);
                            if (VideoWallpaperPolicy.validate(file.getName(), file.length(), info.width, info.height,
                                    info.durationMs, info.fps, info.mime, info.sdr) != VideoWallpaperPolicy.Result.VALID)
                                throw new IOException("此视频的原始规格暂不支持");
                            needsProbe = info.needsNativeSurface(file.length()) && !info.canPlayOriginal(this, file);
                            // The probe verifies original bytes itself; do not read a 1.9 GiB source twice.
                            if (!needsProbe && !candidate.sha256.isEmpty()
                                    && !candidate.sha256.equalsIgnoreCase(VideoWallpaperImport.sha256(file)))
                                throw new IOException("视频校验失败，请重新导入");
                        }
                    } catch (Exception error) { failure = error.getMessage(); }
                    final String message = failure;
                    final boolean verifyHardware = needsProbe;
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing() || !dialog.isShowing()) return;
                        row.setEnabled(true);
                        label.setText(candidate.title + (candidate.isDynamic() ? "\n动态 · 原始规格" : "\n图片"));
                        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        else if (verifyHardware) {
                            dialog.dismiss();
                            startActivityForResult(new Intent(this, VideoCompatibilityProbeActivity.class)
                                    .putExtra("wallpaper_id", candidate.id), REQUEST_VIDEO_PROBE);
                        } else { dialog.dismiss(); showCandidatePreview(candidate); }
                    });
                });
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(96)); lp.bottomMargin = dp(10); list.addView(row, lp);
        }
        if (list.getChildCount() == 0) {
            TextView empty = new TextView(this); empty.setText("暂无可用动态壁纸，请导入视频");
            empty.setTextColor(Color.WHITE); empty.setTextSize(18); list.addView(empty);
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(350)); slp.topMargin = dp(12); card.addView(scroll, slp);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); TextView importVideo = action("导入视频"); importVideo.setOnClickListener(v -> { dialog.dismiss(); openVideoDocument(); }); TextView cancel = action("返回"); cancel.setOnClickListener(v -> dialog.dismiss()); actions.addView(importVideo); actions.addView(cancel); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(50)); alp.topMargin = dp(14); card.addView(actions, alp);
        dialog.setContentView(card); dialog.setCanceledOnTouchOutside(true); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); }
        dialog.show(); if (window != null) window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(72)), -2);
    }

    private void toggleWallpaperPlaylist() {
        WallpaperPlaylistStore store = new WallpaperPlaylistStore(this);
        List<WallpaperCandidate> visible = WallpaperCatalog.visibleVideos(Config.dir(this));
        List<String> ids = WallpaperPlaylistPolicy.normalizeSelection(visible, store.ids());
        if (store.enabled()) {
            store.setEnabled(false, store.currentId());
            setResult(RESULT_OK);
            Toast.makeText(this, "壁纸轮播已关闭，恢复单张壁纸", Toast.LENGTH_SHORT).show();
            refresh();
            return;
        }
        if (!WallpaperPlaylistPolicy.canEnable(ids)) {
            Toast.makeText(this, "请至少选择一张动态壁纸", Toast.LENGTH_SHORT).show();
            showWallpaperPlaylistPicker();
            return;
        }
        String fixed = choices.getString(Config.WALLPAPER_CHOICE_KEY, "");
        String current = ids.contains(store.currentId()) ? store.currentId() : ids.contains(fixed) ? fixed : ids.get(0);
        if (!fixed.equals(current)) WallpaperPlaylistStore.clearBookmark(this, current);
        store.enableWithSelection(ids, current);
        setResult(RESULT_OK);
        Toast.makeText(this, "壁纸轮播已开启", Toast.LENGTH_SHORT).show();
        refresh();
    }

    /** Selector edits only an in-memory ordered ID set; Save is the single persistence boundary. */
    private void showWallpaperPlaylistPicker() {
        wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
        List<WallpaperCandidate> visible = WallpaperCatalog.visibleVideos(Config.dir(this));
        WallpaperPlaylistStore store = new WallpaperPlaylistStore(this);
        List<String> saved = WallpaperPlaylistPolicy.normalizeSelection(visible, store.ids());
        Set<String> selected = new LinkedHashSet<>(saved);
        Dialog dialog = new FrostedDialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24), dp(20), dp(24), dp(18));
        card.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 24, true));
        TextView title = new TextView(this);
        title.setText("选择轮播壁纸"); title.setTextColor(Color.WHITE); title.setTextSize(26);
        card.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        List<TextView> labels = new ArrayList<>();
        List<WallpaperCandidate> rows = new ArrayList<>();
        for (WallpaperCandidate candidate : visible) {
            rows.add(candidate);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true);
            row.setPadding(dp(10), dp(6), dp(14), dp(6));
            row.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14));
            ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap image = candidateThumbnail(candidate); if (image != null) thumb.setImageBitmap(image);
            row.addView(thumb, new LinearLayout.LayoutParams(dp(150), dp(84)));
            TextView label = new TextView(this); label.setTextColor(Color.WHITE); label.setTextSize(18);
            label.setGravity(Gravity.CENTER_VERTICAL); label.setPadding(dp(16), 0, 0, 0);
            labels.add(label); row.addView(label, new LinearLayout.LayoutParams(0, dp(84), 1f));
            row.setOnClickListener(v -> {
                if (!selected.add(candidate.id)) selected.remove(candidate.id);
                updatePlaylistLabels(rows, labels, selected);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(96));
            lp.bottomMargin = dp(10); list.addView(row, lp);
        }
        if (rows.isEmpty()) {
            TextView empty = new TextView(this); empty.setText("暂无可用动态壁纸");
            empty.setTextColor(Color.WHITE); empty.setTextSize(18); list.addView(empty);
        }
        updatePlaylistLabels(rows, labels, selected);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(350));
        slp.topMargin = dp(12); card.addView(scroll, slp);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END);
        TextView save = action("保存");
        save.setOnClickListener(v -> {
            List<String> ordered = WallpaperPlaylistPolicy.reconcileSelection(visible, saved,
                    new ArrayList<>(selected));
            if (ordered.isEmpty()) {
                // Saving an empty active list is an atomic disable; when already off it simply
                // clears the ordered intent and does not enable anything.
                store.saveSelection(ordered);
                if (store.enabled()) store.setEnabled(false, store.currentId());
                setResult(RESULT_OK); dialog.dismiss(); refresh();
                return;
            }
            String oldCurrent = store.currentId();
            store.saveSelection(ordered);
            if (store.enabled() && !ordered.contains(oldCurrent))
                WallpaperPlaylistStore.clearBookmark(this, ordered.get(0));
            setResult(RESULT_OK); dialog.dismiss(); refresh();
        });
        TextView cancel = action("取消"); cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(save); actions.addView(cancel);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(50));
        alp.topMargin = dp(14); card.addView(actions, alp);
        dialog.setContentView(card); dialog.setCanceledOnTouchOutside(false);
        Window window = dialog.getWindow();
        if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); }
        dialog.show();
        if (window != null) window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(72)), -2);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private void updatePlaylistLabels(List<WallpaperCandidate> rows, List<TextView> labels, Set<String> selected) {
        int sequence = 1;
        for (int i = 0; i < rows.size(); i++) {
            WallpaperCandidate candidate = rows.get(i);
            boolean checked = selected.contains(candidate.id);
            labels.get(i).setText((checked ? "✓ " : "○ ") + candidate.title
                    + (checked ? "\n轮播顺序 " + sequence++ : "\n动态 · 原始规格"));
        }
    }

    private TextView action(String text) { TextView view = new TextView(this); view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(17); view.setGravity(Gravity.CENTER); view.setFocusable(true); view.setClickable(true); view.setPadding(dp(12), 0, dp(12), 0); view.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f); lp.setMarginStart(dp(6)); view.setLayoutParams(lp); return view; }

    private Bitmap candidateThumbnail(WallpaperCandidate candidate) {
        File root = Config.dir(this); File file = candidate.posterRelativeFile.isEmpty() ? WallpaperCatalog.resolve(root, candidate.relativeFile) : WallpaperCatalog.resolve(root, candidate.posterRelativeFile); return file == null ? null : decodeBackdrop(file);
    }

    private void showCandidatePreview(WallpaperCandidate candidate) {
        closePreview();
        Dialog dialog = new FrostedDialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24), dp(20), dp(24), dp(18)); card.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 24, true)); TextView title = new TextView(this); title.setText(candidate.title + (candidate.isDynamic() ? " · 动态" : "")); title.setTextColor(Color.WHITE); title.setTextSize(26); card.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        FrameLayout media = new FrameLayout(this); ImageView poster = new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); Bitmap thumb = candidateThumbnail(candidate); if (thumb != null) poster.setImageBitmap(thumb); media.addView(poster, new FrameLayout.LayoutParams(-1, dp(270))); final VideoWallpaperPlayer[] player = new VideoWallpaperPlayer[1]; if (candidate.isDynamic()) { File file = WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile); if (file != null) { TextureView texture = new TextureView(this); media.addView(texture, new FrameLayout.LayoutParams(-1, dp(270))); player[0] = new VideoWallpaperPlayer(this, texture, file, BackgroundResourceCoordinator.Owner.PREVIEW, () -> poster.setVisibility(View.VISIBLE)); } } card.addView(media);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); TextView apply = action("设为壁纸"); apply.setOnClickListener(v -> { dialog.dismiss(); applyCandidate(candidate); }); TextView back = action("返回"); back.setOnClickListener(v -> dialog.dismiss()); actions.addView(apply); actions.addView(back); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(50)); alp.topMargin = dp(14); card.addView(actions, alp); dialog.setContentView(card); dialog.setOnShowListener(v -> { if (player[0] != null) player[0].start(); }); dialog.setOnDismissListener(v -> { if (player[0] != null) player[0].close(); if (activePreview == dialog) activePreview = null; }); activePreview = dialog; Window window = dialog.getWindow(); if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dialog.show(); if (window != null) window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(72)), -2);
    }

    private void applyCandidate(WallpaperCandidate candidate) {
        // Choosing a fixed wallpaper is an atomic mode change: playlist intent/list remains,
        // but no automatic EOS callback may continue after the user explicitly chose this item.
        SharedPreferences.Editor editor = choices.edit()
                .putBoolean(WallpaperPlaylistStore.ENABLED_KEY, false)
                .putString("background_mode", candidate.isDynamic() ? "video" : "image");
        if (candidate.isDynamic()) { editor.putBoolean("static_cover", false); staticCover = false; }
        if (candidate.type == WallpaperCandidate.Type.DEFAULT) editor.remove(Config.WALLPAPER_CHOICE_KEY).remove("video_wallpaper_path");
        else if (candidate.isDynamic()) editor.putString(Config.WALLPAPER_CHOICE_KEY, candidate.id).putString("video_wallpaper_path", WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile).getAbsolutePath());
        else editor.putString(Config.WALLPAPER_CHOICE_KEY, new File(candidate.relativeFile).getName()).remove("video_wallpaper_path");
        editor.commit(); setResult(RESULT_OK);
        Toast.makeText(this, candidate.isDynamic() ? "已切换为单张播放" : getString(R.string.wallpaper_applied), Toast.LENGTH_SHORT).show();
        refresh(); showSettingsBackdrop(Config.load(this));
    }

    private WallpaperCandidate findCandidate(String id) { for (WallpaperCandidate candidate : wallpaperCatalog) if (candidate.id.equals(id)) return candidate; return null; }

    private WallpaperCandidate findCandidateForSelection(String value) {
        if (value == null || value.isEmpty()) return null;
        for (WallpaperCandidate candidate : wallpaperCatalog) {
            if (candidate.id.equals(value) || candidate.relativeFile.equals(value)
                    || (!candidate.relativeFile.isEmpty() && new File(candidate.relativeFile).getName().equals(value))
                    || (!candidate.relativeFile.isEmpty() && new File(candidate.relativeFile).getName().equals(new File(value).getName()))) return candidate;
        }
        return null;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openWallpaperDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.wallpaper_picker_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showWallpaperIdlePicker() {
        final int[] minutes = {0, 1, 2, 4, 5, 10, 15, 30};
        String[] labels = {"关闭", "1 分钟", "2 分钟", "4 分钟", "5 分钟", "10 分钟", "15 分钟", "30 分钟"};
        int current = WallpaperIdlePolicy.normalizeMinutes(choices.getInt(WallpaperIdlePolicy.KEY, 4));
        int selected = 3;
        for (int i = 0; i < minutes.length; i++) if (minutes[i] == current) selected = i;
        new GlassDialog.Builder(this).setTitle("闲置后只显示壁纸")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    choices.edit().putInt(WallpaperIdlePolicy.KEY, minutes[which]).apply();
                    setResult(RESULT_OK); dialog.dismiss(); refresh();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showGlassBlurPicker() {
        String[] labels = {"关闭柔和", "柔和", "明显"};
        new GlassDialog.Builder(this).setTitle("设置背景模糊").setSingleChoiceItems(labels,
                glassBlurRadius == 0 ? 0 : glassBlurRadius >= 8 ? 2 : 1, (dialog, which) -> {
                    glassBlurRadius = which == 0 ? 0 : which == 1 ? 4 : 8;
                    choices.edit().putInt("glass_blur_radius", glassBlurRadius).apply();
                    setResult(RESULT_OK); dialog.dismiss(); refresh(); showSettingsBackdrop(Config.load(this));
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void showWallpaperDimPicker() {
        int current = WallpaperImport.normalizeDim(choices.getInt(
                Config.WALLPAPER_DIM_KEY, Config.DEFAULT_WALLPAPER_DIM));
        String[] labels = new String[8];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = getString(R.string.wallpaper_dim_percent, i * 10);
        }
        new GlassDialog.Builder(this)
                .setTitle(R.string.wallpaper_dim_label)
                .setSingleChoiceItems(labels, current / 10, (dialog, which) -> {
                    choices.edit().putInt(Config.WALLPAPER_DIM_KEY, which * 10).apply();
                    setResult(RESULT_OK);
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openVideoDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_VIDEO_WALLPAPER);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.wallpaper_picker_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void showVideoWallpaperPicker() {
        showWallpaperGallery();
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        WallpaperCandidate selectedWallpaper = findCandidateForSelection(wallpaperCurrent);
        int dim = WallpaperImport.normalizeDim(choices.getInt(
                Config.WALLPAPER_DIM_KEY, Config.DEFAULT_WALLPAPER_DIM));
        wallpaperDimChoice.setText(getString(R.string.wallpaper_dim_state, dim));
        videoWallpaperChoice.setText("选择壁纸：" + (selectedWallpaper != null ? selectedWallpaper.title
                : "none".equals(wallpaperCurrent) ? "无" : "跟随主题")
                + (selectedWallpaper != null && selectedWallpaper.isDynamic() ? " · 动态" : ""));
        WallpaperPlaylistStore playlist = new WallpaperPlaylistStore(this);
        List<String> ids = WallpaperPlaylistPolicy.normalizeSelection(
                WallpaperCatalog.visibleVideos(Config.dir(this)), playlist.ids());
        wallpaperPlaylistSwitch.setText("壁纸轮播：" + (playlist.enabled() ? "开启" : "关闭"));
        wallpaperPlaylistChoice.setText("选择轮播壁纸：已选 " + ids.size() + " 张 · 播完切换");
        int idleMinutes = WallpaperIdlePolicy.normalizeMinutes(choices.getInt(WallpaperIdlePolicy.KEY, 4));
        ((TextView) findViewById(R.id.homeScreensaverChoice)).setText("壁纸屏保："
                + (idleMinutes == 0 ? "关闭" : idleMinutes + " 分钟"));
        if (glassBlurChoice != null) glassBlurChoice.setText("设置背景模糊：" + (glassBlurRadius == 0 ? "关闭" : glassBlurRadius >= 8 ? "明显" : "柔和"));
        if (reducedMotionChoice != null) reducedMotionChoice.setText("减少动态效果：" + (reducedMotion ? "开" : "关"));
        if (staticCoverChoice != null) staticCoverChoice.setText("使用静态封面：" + (staticCover ? "开" : "关"));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY)
                || choices.contains(Config.WALLPAPER_DIM_KEY)
                || choices.contains("video_wallpaper_path") || choices.contains("glass_blur_radius")
                || choices.contains("reduced_motion") || choices.contains("static_cover")
                || choices.contains(WallpaperPlaylistStore.ENABLED_KEY)
                || choices.contains(WallpaperPlaylistStore.IDS_KEY);
    }

    private void refreshHomeStatus(TextView choice) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        android.content.pm.ResolveInfo resolved = getPackageManager().resolveActivity(home, 0);
        String pkg = resolved == null || resolved.activityInfo == null ? "" : resolved.activityInfo.packageName;
        choice.setText(pkg.equals(getPackageName()) ? "默认桌面：TVHome（当前解析）" : "默认桌面：小米桌面");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_PROBE) {
            if (resultCode == RESULT_OK && data != null) {
                wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
                WallpaperCandidate candidate = findCandidate(data.getStringExtra("wallpaper_id"));
                if (candidate != null) showCandidatePreview(candidate);
            } else Toast.makeText(this, "本机原规格验证未通过，已保留当前壁纸和源文件", Toast.LENGTH_LONG).show();
            return;
        }
        if (requestCode == MainActivity.DOCK_EDITOR_REQUEST) {
            if (resultCode == RESULT_OK) setResult(RESULT_OK);
            return;
        }
        if (requestCode == REQUEST_VIDEO_WALLPAPER) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            wallpaperImporter.execute(() -> {
                try {
                    File imported = VideoWallpaperImport.importVideo(this, uri);
                    WallpaperCandidate importedCandidate = WallpaperCatalog.videoFor(Config.dir(this), imported, "动态壁纸");
                    WallpaperCatalog.upsert(Config.dir(this), importedCandidate);
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        choices.edit().putString("background_mode", "video").putBoolean("static_cover", false)
                                .putString("video_wallpaper_path", imported.getAbsolutePath())
                                .putString(Config.WALLPAPER_CHOICE_KEY, importedCandidate.id).apply();
                        staticCover = false;
                        setResult(RESULT_OK);
                        Toast.makeText(this, R.string.video_wallpaper_imported, Toast.LENGTH_SHORT).show();
                        wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
                        refresh();
                    });
                } catch (IOException | RuntimeException failure) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.video_wallpaper_import_failed,
                            Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        if (requestCode != REQUEST_WALLPAPER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        wallpaperImporter.execute(() -> {
            try {
                File imported = WallpaperImport.importImage(this, uri);
                WallpaperCatalog.upsert(Config.dir(this), WallpaperCatalog.imageFor(Config.dir(this), imported, "自定义壁纸"));
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, imported.getName())
                            .putString("background_mode", "image").apply();
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.wallpaper_imported, Toast.LENGTH_SHORT).show();
                    wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
                    refresh();
                });
            } catch (IOException | RuntimeException failure) {
                runOnUiThread(() -> Toast.makeText(this, R.string.wallpaper_import_failed,
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        closePreview();
        if (settingsBackdrop != null) settingsBackdrop.close();
        wallpaperImporter.shutdownNow();
        super.onDestroy();
    }

    private void open(Intent intent) {
        try {
            startActivity(SettingsIntents.fromActivity(this, intent));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
