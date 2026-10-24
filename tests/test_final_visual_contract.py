from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
LAYOUT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "layout" / "activity_main.xml"
POLICY = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "AppleLayoutPolicy.java"
SETTINGS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "LauncherSettingsActivity.java"
SETTINGS_LAYOUT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "layout" / "activity_launcher_settings.xml"


def test_final_home_keeps_legacy_header_gone_in_runtime():
    text = MAIN.read_text(encoding="utf-8")
    assert "clockBlock.setVisibility(View.GONE);" in text
    assert "clockBlock.setVisibility(valid ? View.VISIBLE : View.INVISIBLE);" not in text


def test_final_home_has_one_compact_time_and_no_default_second_row():
    text = LAYOUT.read_text(encoding="utf-8")
    assert text.count('android:id="@+id/appleTime"') == 1
    assert 'android:id="@+id/leftBlock"' in text
    assert 'android:visibility="gone"' in text


def test_settings_pill_opens_tvhome_settings_directly():
    text = MAIN.read_text(encoding="utf-8")
    assert "btnSettings.setOnClickListener(v -> startActivityForResult(" in text
    assert "new Intent(this, LauncherSettingsActivity.class)" in text
    assert "btnSettings.setOnClickListener(v -> open(systemIntent(Settings.ACTION_SETTINGS)))" not in text


def test_dock_policy_stays_inside_the_layout_safe_rect():
    text = POLICY.read_text(encoding="utf-8")
    assert "return availableWidthPx;" in text
    assert "0.93f" not in text


def test_tvhome_settings_reuses_the_wallpaper_backdrop():
    activity = SETTINGS.read_text(encoding="utf-8")
    layout = SETTINGS_LAYOUT.read_text(encoding="utf-8")
    assert "SettingsBackdropRenderer" in activity
    assert "showOwnedSnapshot" in activity
    assert "new WallpaperPlayer(this, settingsRoot, config)" not in activity
    assert 'android:id="@+id/settingsRoot"' in layout
    assert 'android:id="@+id/settingsBackdrop"' in layout
    assert 'android:background="@android:color/transparent"' in layout


def test_layout_does_not_reset_the_list_while_navigating_down():
    # The old contract required the unconditional reset that caused the reported regression.
    text = MAIN.read_text(encoding="utf-8")
    start = text.index("grid.addOnLayoutChangeListener")
    end = text.index("root.getViewTreeObserver().addOnPreDrawListener", start)
    layout_listener = text[start:end]
    assert "parkDockOnlyRow" not in layout_listener
    assert "scrollToPosition" not in layout_listener
    assert "grid.post(this::refitGlass);" in layout_listener


def test_refit_glass_reacts_to_position_changes_not_only_size():
    text = MAIN.read_text(encoding="utf-8")
    start = text.index("private void refitGlass()")
    end = text.index("// ---- top shelf", start)
    refit = text[start:end]
    # The old spring target checks cannot catch a stale *actual* outline position after pause.
    # This static contract guards the anchoring path; real alignment is verified on the TV.
    assert "getCurrentFocus()" in refit
    assert "card.getLocationOnScreen" in refit
    assert "root.getLocationOnScreen" in refit
    assert "hideGlass(); return;" in refit
    assert "glassScrollShift" not in text
