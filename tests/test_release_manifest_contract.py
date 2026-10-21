from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INSTALLER = ROOT / "Install-TVHome.ps1"


def test_installer_fails_closed_on_schema_v2_fields():
    text = INSTALLER.read_text(encoding="utf-8")
    for required in (
        "schemaVersion",
        "versionCode",
        "versionName",
        "apk.sizeBytes",
        "certificateSha256",
        "sourceCommit",
        "build.compileSdk",
        "build.targetSdk",
    ):
        assert required in text
    assert "release-manifest.json" in text


def test_installer_checks_apk_hash_and_size_before_adb_mutation():
    text = INSTALLER.read_text(encoding="utf-8")
    assert "Get-FileHash" in text
    assert "Length -ne [int64]$value.apk.sizeBytes" in text
    assert text.index("$apkHash") < text.index("$installOutput = Invoke-AdbText")
