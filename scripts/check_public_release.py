#!/usr/bin/env python3
"""Fail-closed privacy checks for the sanitized TVHome source and release assets."""

from __future__ import annotations

import argparse
import re
import sys
import zipfile
from pathlib import Path


SKIP_PARTS = {".git", ".gradle", "build", "__pycache__", "private", "runs"}
SECRET_MARKERS = (
    "BEGIN " + "PRIVATE KEY",
    "BEGIN " + "RSA PRIVATE KEY",
    "BEGIN " + "OPENSSH PRIVATE KEY",
)
TOKEN_PATTERNS = (
    re.compile(r"(?:ghp|github_pat|glpat|xox[baprs])-[A-Za-z0-9_-]{16,}"),
    re.compile(r"(?:sk|rk)-[A-Za-z0-9_-]{20,}"),
    re.compile(r"AKIA[0-9A-Z]{16}"),
)
PRIVATE_IP_PATTERNS = (
    re.compile(r"(?<![0-9.])10\.(?:[0-9]{1,3}\.){2}[0-9]{1,3}(?![0-9.])"),
    re.compile(r"(?<![0-9.])192\.168\.(?:[0-9]{1,3}\.)[0-9]{1,3}(?![0-9.])"),
    re.compile(r"(?<![0-9.])172\.(?:1[6-9]|2[0-9]|3[01])\.(?:[0-9]{1,3}\.)[0-9]{1,3}(?![0-9.])"),
)
WINDOWS_USER_PATH = re.compile(r"[A-Z]:" + r"\\Users" + r"\\[^\s\\/]+", re.IGNORECASE)
FORBIDDEN_WORDS = ("WON" + "JEAN", "WAR" + "LIN", "BRU" + "CE")
SECRET_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", "local.properties"}


def iter_source_files(root: Path):
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        try:
            relative = path.relative_to(root)
        except ValueError:
            continue
        if any(part in SKIP_PARTS for part in relative.parts):
            continue
        yield path


def scan_bytes(label: str, data: bytes, denylist: list[str], findings: list[str]) -> None:
    text = data.decode("utf-8", errors="ignore")
    for marker in SECRET_MARKERS:
        if marker in text:
            findings.append(f"private-key marker: {label}")
    for pattern in TOKEN_PATTERNS:
        if pattern.search(text):
            findings.append(f"token pattern: {label}")
    for pattern in PRIVATE_IP_PATTERNS:
        if pattern.search(text):
            findings.append(f"private address: {label}")
    if WINDOWS_USER_PATH.search(text):
        findings.append(f"Windows user path: {label}")
    upper = text.upper()
    for word in FORBIDDEN_WORDS:
        if word in upper:
            findings.append(f"private/company name: {label}")
    for value in denylist:
        if value and value in text:
            findings.append(f"denylisted value: {label}")


def scan_path(path: Path, label: str, denylist: list[str], findings: list[str]) -> int:
    if not path.exists():
        findings.append(f"missing asset: {label}")
        return 0
    if path.is_dir():
        count = 0
        for item in iter_source_files(path):
            if item.suffix.lower() in SECRET_SUFFIXES or item.name.lower() in SECRET_SUFFIXES:
                findings.append(f"secret-looking file: {item.relative_to(path)}")
            scan_bytes(str(item.relative_to(path)), item.read_bytes(), denylist, findings)
            count += 1
        return count
    if path.suffix.lower() == ".zip" or path.suffix.lower() == ".apk":
        count = 0
        try:
            with zipfile.ZipFile(path) as archive:
                for info in archive.infolist():
                    name = info.filename
                    lowered = name.lower()
                    if lowered.endswith(tuple(SECRET_SUFFIXES)) or lowered in SECRET_SUFFIXES:
                        findings.append(f"secret-looking archive entry: {label}!{name}")
                    scan_bytes(f"{label}!{name}", archive.read(info), denylist, findings)
                    count += 1
        except (OSError, zipfile.BadZipFile) as exc:
            findings.append(f"unreadable archive: {label} ({exc})")
        return count
    scan_bytes(label, path.read_bytes(), denylist, findings)
    return 1


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--zip", dest="zip_path", type=Path)
    parser.add_argument("--denylist", type=Path)
    args = parser.parse_args(argv)

    denylist = []
    if args.denylist:
        denylist = [line.strip() for line in args.denylist.read_text(encoding="utf-8").splitlines() if line.strip()]
    findings: list[str] = []
    files = scan_path(args.source, "source", denylist, findings)
    if args.apk:
        files += scan_path(args.apk, "apk", denylist, findings)
    if args.zip_path:
        files += scan_path(args.zip_path, "install-kit", denylist, findings)
    if findings:
        print("PUBLIC RELEASE CHECK: FAIL")
        for finding in sorted(set(findings)):
            print(f"- {finding}")
        return 1
    print(f"PUBLIC RELEASE CHECK: PASS ({files} files/assets scanned)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
