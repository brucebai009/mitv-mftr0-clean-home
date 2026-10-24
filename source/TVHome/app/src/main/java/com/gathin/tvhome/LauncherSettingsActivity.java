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
import java.util.List;
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
    private TextView wallpaperChoice;
    private TextView wallpaperDimChoice;
    private TextView videoWallpaperChoice;
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
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperGallery());
        wallpaperDimChoice = findViewById(R.id.wallpaperDimChoice);
        wallpaperDimChoice.setOnClickListener(v -> showWallpaperDimPicker());
        videoWallpaperChoice = findViewById(R.id.videoWallpaperChoice);
        videoWallpaperChoice.setOnClickListener(v -> showWallpaperGallery());
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
                    .remove("reduced_motion").remove("static_cover").commit();
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
        homeHelperChoice.setOnClickListener(v -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        refreshHomeHelper(homeHelperChoice);
        findViewById(R.id.homeSignalSourceChoice).setOnClickListener(v ->
                open(new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_OPEN_INPUTS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)));
        findViewById(R.id.homeScreensaverChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_DREAM_SETTINGS)));
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
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String service = getPackageName() + "/" + HomeOnlyAccessibilityService.class.getName();
        boolean active = HomeBootReceiver.isHelperEnabledByUser(this) && RecoveryState.helperEnabled(this);
        choice.setText(active ? R.string.home_helper_on : R.string.home_helper_off);
        choice.setContentDescription(getString(R.string.home_helper_open_settings));
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
        int[] ids = {R.id.wallpaperChoice, R.id.wallpaperDimChoice, R.id.glassBlurChoice, R.id.videoWallpaperChoice,
                R.id.followThemeChoice, R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7, R.id.rows0,
                R.id.rows1, R.id.rows2, R.id.rows3, R.id.homeAppsChoice, R.id.animationsChoice, R.id.advancedChoice,
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
        category.setOnClickListener(v -> settingsScroll.post(() -> settingsScroll.smoothScrollTo(0, section.getTop())));
    }

    private void showWallpaperGallery() {
        wallpaperCatalog = WallpaperCatalog.load(Config.dir(this));
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24), dp(20), dp(24), dp(18));
        card.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 24));
        TextView title = new TextView(this); title.setText("壁纸"); title.setTextColor(Color.WHITE); title.setTextSize(26); card.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        for (WallpaperCandidate candidate : wallpaperCatalog) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setFocusable(true); row.setClickable(true); row.setPadding(dp(10), dp(6), dp(14), dp(6)); row.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14));
            ImageView thumb = new ImageView(this); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); Bitmap image = candidateThumbnail(candidate); if (image != null) thumb.setImageBitmap(image); row.addView(thumb, new LinearLayout.LayoutParams(dp(150), dp(84)));
            TextView label = new TextView(this); label.setText(candidate.title + (candidate.isDynamic() ? "\n动态" : candidate.type == WallpaperCandidate.Type.DEFAULT ? "\n系统默认" : "\n图片")); label.setTextColor(Color.WHITE); label.setTextSize(18); label.setGravity(Gravity.CENTER_VERTICAL); label.setPadding(dp(16), 0, 0, 0); row.addView(label, new LinearLayout.LayoutParams(0, dp(84), 1f));
            row.setOnClickListener(v -> { dialog.dismiss(); showCandidatePreview(candidate); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(96)); lp.bottomMargin = dp(10); list.addView(row, lp);
        }
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(350)); slp.topMargin = dp(12); card.addView(scroll, slp);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); TextView importImage = action("导入图片"); importImage.setOnClickListener(v -> { dialog.dismiss(); openWallpaperDocument(); }); TextView importVideo = action("导入视频"); importVideo.setOnClickListener(v -> { dialog.dismiss(); openVideoDocument(); }); TextView cancel = action("返回"); cancel.setOnClickListener(v -> dialog.dismiss()); actions.addView(importImage); actions.addView(importVideo); actions.addView(cancel); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(50)); alp.topMargin = dp(14); card.addView(actions, alp);
        dialog.setContentView(card); dialog.setCanceledOnTouchOutside(true); Window window = dialog.getWindow(); if (window != null) { window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); }
        dialog.show(); if (window != null) window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(72)), -2);
    }

    private TextView action(String text) { TextView view = new TextView(this); view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(17); view.setGravity(Gravity.CENTER); view.setFocusable(true); view.setClickable(true); view.setPadding(dp(12), 0, dp(12), 0); view.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 14)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f); lp.setMarginStart(dp(6)); view.setLayoutParams(lp); return view; }

    private Bitmap candidateThumbnail(WallpaperCandidate candidate) {
        File root = Config.dir(this); File file = candidate.posterRelativeFile.isEmpty() ? WallpaperCatalog.resolve(root, candidate.relativeFile) : WallpaperCatalog.resolve(root, candidate.posterRelativeFile); return file == null ? null : decodeBackdrop(file);
    }

    private void showCandidatePreview(WallpaperCandidate candidate) {
        closePreview();
        Dialog dialog = new Dialog(this); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE); LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24), dp(20), dp(24), dp(18)); card.setBackground(new GlassPanelDrawable(getResources().getDisplayMetrics().density, 24)); TextView title = new TextView(this); title.setText(candidate.title + (candidate.isDynamic() ? " · 动态" : "")); title.setTextColor(Color.WHITE); title.setTextSize(26); card.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));
        FrameLayout media = new FrameLayout(this); ImageView poster = new ImageView(this); poster.setScaleType(ImageView.ScaleType.CENTER_CROP); Bitmap thumb = candidateThumbnail(candidate); if (thumb != null) poster.setImageBitmap(thumb); media.addView(poster, new FrameLayout.LayoutParams(-1, dp(270))); final VideoWallpaperPlayer[] player = new VideoWallpaperPlayer[1]; if (candidate.isDynamic()) { File file = WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile); if (file != null) { TextureView texture = new TextureView(this); media.addView(texture, new FrameLayout.LayoutParams(-1, dp(270))); player[0] = new VideoWallpaperPlayer(this, texture, file, BackgroundResourceCoordinator.Owner.PREVIEW, () -> poster.setVisibility(View.VISIBLE)); } } card.addView(media);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END); TextView apply = action("设为壁纸"); apply.setOnClickListener(v -> { applyCandidate(candidate); dialog.dismiss(); }); TextView back = action("返回"); back.setOnClickListener(v -> dialog.dismiss()); actions.addView(apply); actions.addView(back); LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, dp(50)); alp.topMargin = dp(14); card.addView(actions, alp); dialog.setContentView(card); dialog.setOnShowListener(v -> { if (player[0] != null) player[0].start(); }); dialog.setOnDismissListener(v -> { if (player[0] != null) player[0].close(); if (activePreview == dialog) activePreview = null; }); activePreview = dialog; Window window = dialog.getWindow(); if (window != null) window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dialog.show(); if (window != null) window.setLayout(Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(72)), -2);
    }

    private void applyCandidate(WallpaperCandidate candidate) {
        SharedPreferences.Editor editor = choices.edit().putString("background_mode", candidate.isDynamic() ? "video" : "image");
        if (candidate.type == WallpaperCandidate.Type.DEFAULT) editor.remove(Config.WALLPAPER_CHOICE_KEY).remove("video_wallpaper_path");
        else if (candidate.isDynamic()) editor.putString(Config.WALLPAPER_CHOICE_KEY, candidate.id).putString("video_wallpaper_path", WallpaperCatalog.resolve(Config.dir(this), candidate.relativeFile).getAbsolutePath());
        else editor.putString(Config.WALLPAPER_CHOICE_KEY, new File(candidate.relativeFile).getName()).remove("video_wallpaper_path");
        editor.apply(); setResult(RESULT_OK); Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show(); refresh(); showSettingsBackdrop(Config.load(this));
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
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : selectedWallpaper != null ? selectedWallpaper.title
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        int dim = WallpaperImport.normalizeDim(choices.getInt(
                Config.WALLPAPER_DIM_KEY, Config.DEFAULT_WALLPAPER_DIM));
        wallpaperDimChoice.setText(getString(R.string.wallpaper_dim_state, dim));
        String videoPath = choices.getString("video_wallpaper_path", "");
        WallpaperCandidate selectedVideo = findCandidateForSelection(videoPath);
        videoWallpaperChoice.setText(getString(R.string.video_wallpaper_state,
                videoPath.isEmpty() ? getString(R.string.video_wallpaper_none)
                        : selectedVideo != null ? selectedVideo.title : new File(videoPath).getName()));
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
                || choices.contains("reduced_motion") || choices.contains("static_cover");
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
        if (requestCode == REQUEST_VIDEO_WALLPAPER) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri uri = data.getData();
            wallpaperImporter.execute(() -> {
                try {
                    File imported = VideoWallpaperImport.importVideo(this, uri);
                    WallpaperCatalog.upsert(Config.dir(this), WallpaperCatalog.videoFor(Config.dir(this), imported, "动态壁纸"));
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        choices.edit().putString("background_mode", "video")
                                .putString("video_wallpaper_path", imported.getAbsolutePath()).apply();
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
