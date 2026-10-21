from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APPLY = ROOT / "Apply-ContentPolicy.ps1"
RESTORE = ROOT / "Restore-ContentPolicy.ps1"


def test_content_policy_is_allowlisted_and_non_destructive():
    text = APPLY.read_text(encoding="utf-8")
    for package in ("com.xiaomi.mitv.appstore", "com.xiaomi.mitv.shop", "com.duokan.videodaily"):
        assert package in text
    for protected in ("com.mitv.tvhome", "com.xiaomi.voicecontrol", "com.xiaomi.mitv.tvplayer"):
        assert protected in text
    assert "pm clear" not in text.lower()
    assert "disable-user" in text


def test_content_policy_has_a_reversible_restore_script():
    text = RESTORE.read_text(encoding="utf-8")
    assert "'enable'" in text
    assert "Snapshot" in text
    assert "com.gathin.tvhome" not in text
