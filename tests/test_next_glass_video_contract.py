from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
SETTINGS = JAVA / "LauncherSettingsActivity.java"
VIDEO_IMPORT = JAVA / "VideoWallpaperImport.java"
WALLPAPER = JAVA / "WallpaperPlayer.java"
THEME_FILES = JAVA / "ThemeFiles.java"
VIDEO_SCRIPT = ROOT / "Import-VideoWallpaper.ps1"
MAIN_LAYOUT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "layout" / "activity_main.xml"


def test_catalog_contract_is_additive_and_schema_versioned():
    text = (JAVA / "WallpaperCatalog.java").read_text(encoding="utf-8")
    text += (JAVA / "Config.java").read_text(encoding="utf-8")
    text += (JAVA / "WallpaperPlayer.java").read_text(encoding="utf-8")
    text += SETTINGS.read_text(encoding="utf-8")
    assert "SCHEMA_VERSION = 1" in text
    assert "wallpaper_catalog.json" in text
    for key in ("background_mode", "video_wallpaper_path", "wallpaper_choice", "wallpaper_dim"):
        assert key in text
    assert "canonical" in text.lower()
    assert "Atomic" in text or "ATOMIC_MOVE" in text
    assert "mergeScannedEntries" in text


def test_video_import_rechecks_existing_equal_length_file_hash():
    text = VIDEO_IMPORT.read_text(encoding="utf-8")
    assert "sha256(target)" in text
    assert "target.delete()" in text
    assert "renameTo" in text


def test_home_player_uses_a_pure_background_container():
    text = WALLPAPER.read_text(encoding="utf-8") + (JAVA / "VideoWallpaperPlayer.java").read_text(encoding="utf-8")
    assert "backgroundRoot" in text
    assert "addView(texture" in text
    assert "setVisibility(View.VISIBLE)" in text or "setVisibility(TextureView.VISIBLE)" in text
    layout = MAIN_LAYOUT.read_text(encoding="utf-8")
    assert 'android:id="@+id/backgroundRoot"' in layout


def test_settings_uses_snapshot_backdrop_and_not_a_second_video_player():
    activity = SETTINGS.read_text(encoding="utf-8")
    assert "SettingsBackdropRenderer" in activity
    assert "new WallpaperPlayer(this, settingsRoot" not in activity
    assert "findCandidateForSelection(wallpaperCurrent)" in activity


def test_owned_settings_surfaces_have_no_alert_dialog_builder():
    text = SETTINGS.read_text(encoding="utf-8")
    assert "AlertDialog.Builder" not in text
    assert "GlassDialog.Builder" in text


def test_wallpaper_catalog_is_writable_through_the_existing_provider_without_broadening_paths():
    text = THEME_FILES.read_text(encoding="utf-8")
    assert '"wallpaper_catalog.json"' in text
    assert "wallpaper_catalog.json" in text
    assert "wallpapers" in text and "videos" in text
    assert "content://" not in text


def test_video_import_supports_private_poster_and_metadata_without_hardcoding_device_data():
    text = VIDEO_SCRIPT.read_text(encoding="utf-8")
    assert "PosterPath" in text
    assert "Title" in text
    assert "wallpaper_catalog.json" in text
    assert "Get-FileHash" in text
    assert "content write" in text
    assert "192.168." not in text
