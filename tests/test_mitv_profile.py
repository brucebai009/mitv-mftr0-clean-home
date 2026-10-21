import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROFILE = ROOT / "launcher" / "app" / "src" / "main" / "res" / "raw" / "device_profile_mitv_mftr0.json"
MAIN = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
ADAPTER = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "AppAdapter.java"


def load_profile():
    return json.loads(PROFILE.read_text(encoding="utf-8"))


def test_profile_is_exact_and_preview_defaults_closed():
    profile = load_profile()
    assert profile["schemaVersion"] == 1
    assert profile["model"] == "MiTV-MFTR0"
    assert profile["buildFingerprint"].startswith("xiaomi/freeguy/freeguy:11/")
    assert profile["previewEnabled"] is False


def test_route_extras_are_explicit_integers_and_hw8_is_excluded():
    profile = load_profile()
    inputs = {item["inputId"]: item for item in profile["inputs"]}
    assert inputs["com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"]["extras"]["input"] == 23
    assert inputs["com.mediatek.tvinput/.hdmi.HDMIInputService/HW6"]["extras"]["input"] == 24
    assert inputs["com.mediatek.tvinput/.hdmi.HDMIInputService/HW7"]["extras"]["input"] == 25
    assert all(isinstance(item.get("extras", {}).get("input"), int)
               for item in profile["inputs"] if "input" in item.get("extras", {}))
    assert "com.mediatek.tvinput/.hdmi.HDMIInputService/HW8" in profile["excludedInputIds"]
    assert "HW8" not in [item["inputId"] for item in profile["inputs"]]


def test_source_and_settings_paths_keep_safe_fallbacks():
    main = MAIN.read_text(encoding="utf-8")
    adapter = ADAPTER.read_text(encoding="utf-8")
    assert "deviceProfile.inputRoute(inputId)" in main
    assert "deviceProfile.settingsIntent()" in main
    assert "DeviceProfile.load(ctx)" in adapter
    assert "com.android.tv.settings" in adapter
    assert "systemIntent(Settings.ACTION_SETTINGS)" in main
    assert "catch (SecurityException e)" in main
