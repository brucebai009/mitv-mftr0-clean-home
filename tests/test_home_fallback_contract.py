from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_boot_fallback_is_user_opt_in_and_one_shot():
    manifest = (ROOT / "launcher/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    receiver = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/HomeBootReceiver.java").read_text(encoding="utf-8")
    service = (ROOT / "launcher/app/src/main/java/com/gathin/tvhome/HomeOnlyAccessibilityService.java").read_text(encoding="utf-8")
    assert "android.permission.RECEIVE_BOOT_COMPLETED" in manifest
    assert ".HomeBootReceiver" in manifest
    assert "android.intent.action.BOOT_COMPLETED" in manifest
    assert "ENABLED_ACCESSIBILITY_SERVICES" in receiver
    assert "RecoveryState.bootRecoveryEnabled" in receiver
    assert "startActivity" in receiver
    assert "onServiceConnected" in service
    assert "RecoveryState.markHelperEnabled" in service
    assert "while (" not in receiver
    assert "postDelayed" not in receiver


def test_restore_disarms_only_tvhome_helper_and_recovery_flags():
    restore = (ROOT / "Restore-OriginalHome.ps1").read_text(encoding="utf-8")
    assert "HomeOnlyAccessibilityService" in restore
    assert "enabled_accessibility_services" in restore
    assert "helper_enabled" in restore
    assert "boot_recovery" in restore
    assert "reset_gesture" in restore
    assert "com.mitv.tvhome" not in restore.split("$homeHelperService", 1)[-1]
    assert "value:s:false" in restore
