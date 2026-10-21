from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_install_script_requires_explicit_serial_and_never_uninstalls():
    text = (ROOT / "Install-TVHome.ps1").read_text(encoding="utf-8")
    assert "[Parameter(Mandatory=$true)][string]$Serial" in text
    assert "'install', '-r'" in text
    assert "uninstall" not in text.lower()
    assert "192.168." not in text


def test_restore_script_reads_snapshot_and_verifies_home_readback():
    text = (ROOT / "Restore-OriginalHome.ps1").read_text(encoding="utf-8")
    assert "SnapshotPath" in text
    assert "originalHomeComponent" in text
    assert "set-home-activity" in text
    assert "resolve-activity" in text
    assert "mResumedActivity" in text
    assert "LASTEXITCODE" in text


def test_wallpaper_import_script_uses_safe_provider_name_and_literal_path():
    text = (ROOT / "Import-Wallpaper.ps1").read_text(encoding="utf-8")
    assert "[Parameter(Mandatory=$true)][string]$ImagePath" in text
    assert "-LiteralPath" in text
    assert "content://com.gathin.tvhome.theme/wallpapers/" in text
    assert "192.168." not in text
