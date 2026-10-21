import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCANNER = ROOT / "scripts" / "check_public_release.py"


def run_scanner(source: Path):
    return subprocess.run(
        [sys.executable, str(SCANNER), "--source", str(source)],
        text=True,
        capture_output=True,
    )


def test_scanner_rejects_sensitive_sentinel(tmp_path):
    (tmp_path / "README.md").write_text(
        "fake 192.168.10.20 and -----BEGIN PRIVATE KEY-----\n", encoding="utf-8"
    )
    result = run_scanner(tmp_path)
    assert result.returncode != 0
    assert "private" in result.stdout.lower() or "address" in result.stdout.lower()


def test_scanner_accepts_public_placeholder(tmp_path):
    (tmp_path / "README.md").write_text(
        "Connect with `adb -s <TV_IP:PORT>` and follow the TVHome guide.\n",
        encoding="utf-8",
    )
    result = run_scanner(tmp_path)
    assert result.returncode == 0, result.stdout + result.stderr
