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
    assert "new WallpaperPlayer(this, settingsRoot, config)" in activity
    assert "settingsWallpaper.start();" in activity
    assert 'android:id="@+id/settingsRoot"' in layout
    assert 'android:background="@android:color/transparent"' in layout
