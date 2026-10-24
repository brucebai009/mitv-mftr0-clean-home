from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "launcher" / "app" / "build.gradle"
STRINGS = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "strings.xml"


def test_release_is_unsigned_and_v13_candidate_version_is_locked():
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    assert "versionCode 13" in text
    assert "versionName '1.0.0-v13.6-repair'" in text
    assert "signingConfig = null" in text
    assert "signingConfig signingConfigs.debug" not in text


def test_public_product_name_is_tvhome():
    text = STRINGS.read_text(encoding="utf-8")
    assert '<string name="app_name">TVHome</string>' in text
