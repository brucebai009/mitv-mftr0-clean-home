# MiTV Product Release Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a signed, installable TVHome release candidate from the current implementation, with user wallpaper import, safe deployment/restore tooling, ordinary-UID device verification, and a clean public GitHub source/release package.

**Architecture:** Extend the existing Java Android launcher and its current profile/config/wallpaper abstractions. Additive changes only: keep `com.gathin.tvhome`, the four-column UI, original Xiaomi HOME enabled, existing application data, and current source-routing behavior. Publish through a new sanitized history rather than the internal repository history.

**Tech Stack:** Java Android app; AGP 8.5.2; Gradle 8.9; JDK 17; compile/target SDK 34; min SDK 26; Python release scanner; PowerShell/ADB deployment tools; local signing with a dedicated RSA-3072 key.

**Spec:** `E:\产品图\MiTV_Product_Release_Codex_Task_v2_0.md`

## Global Constraints

- Continue from the actual current checkout; never reset, overwrite, or discard existing work.
- Keep the original Xiaomi desktop enabled and preserve all existing user apps and data.
- Do not use root, system signing, accessibility takeover, Shizuku, Device Owner, or hidden APIs.
- Never publish private device identity, IP/serial, logs, snapshots, credentials, keys, or internal reports.
- Never claim native preview or dynamic-screen behavior from static evidence; distinguish technical evidence from human acceptance.
- Build release unsigned from the exact public source commit, then sign only on this computer.

## Review Focus

- Version/package/signature/SDK and unsigned-release correctness.
- Wallpaper selection, validation, persistence, dimming, and fallback behavior.
- Real ordinary-UID routes, native preview boundary, Home safety, and restore readback.
- Public-source allowlist, scanner coverage, Git author privacy, and artifact/source alignment.
- Final downloaded Release assets match local hashes and install without data loss.

---

## Task 1: Baseline, toolchain, and release contract

- [ ] Record current HEAD/status, device identity, original HOME, installed-package state, and version decision in a private run journal.
- [ ] Inspect the existing profile, wallpaper, deployment, tests, and prior reports without modifying private evidence.
- [ ] Attempt the official local SDK 34/build-tools 34 installation with existing proxy settings and verify metadata; if it remains blocked, record the exact blocker and use the public CI path.
- [ ] Change only release metadata needed for this candidate: TVHome label, version `1.0.0-rc.1`, versionCode 7 unless the real same-package query requires a higher value, and `signingConfig = null`.
- [ ] Run the pre-change test baseline and commit the release-contract changes separately.

## Task 2: TV wallpaper product behavior

- [ ] Write failing unit/static tests for safe image validation, extension/size limits, dimming range/step, and picker/own-copy behavior.
- [ ] Implement an Android 11-compatible `ACTION_OPEN_DOCUMENT` image picker with cancellation preservation and background copy into the app-owned wallpaper collection.
- [ ] Add JPEG/PNG/static-WebP validation, bounded decode checks, deterministic safe filenames, and an additive preference for 0–70% dimming in 10% steps with a 30% default.
- [ ] Update the existing settings UI and Chinese/English resources with the required canonical copy, preserving theme default/none behavior and rotation compatibility.
- [ ] Add focused JVM/static tests, run the affected suite, then commit the wallpaper feature.

## Task 3: Safe install, import, and restore tooling

- [ ] Write tests/sentinel fixtures for argument handling, device identity checks, per-command exit checking, snapshot/journal creation, and readback verification.
- [ ] Replace the deficient internal restore script with a guarded implementation that validates device/user/original HOME and reads back resolver/foreground state.
- [ ] Implement generic public `Install-TVHome.ps1/.cmd`, `Restore-OriginalHome.ps1`, and `Import-Wallpaper.ps1` templates with explicit serial, ADB path, state root, SHA verification, safe arguments, and no prefilled device values.
- [ ] Build a whitelist-only InstallKit from validated files; do not compress internal deliverables wholesale.
- [ ] Run script tests and review the generated package contents.

## Task 4: Build, sign, and technical verification

- [ ] Build the unsigned release with real SDK 34 using either the verified local toolchain or the sanitized GitHub Actions workflow.
- [ ] Create/reuse the dedicated local RSA-3072 signing key only under the private directory; never place it in source, public history, CI, or the InstallKit.
- [ ] Sign, zipalign if needed, and verify package, version, non-debuggable status, certificate subject/digest, and SHA-256.
- [ ] Run the full JVM/static suite, lint, binary/privacy checks, and record technical evidence.

## Task 5: Ordinary-UID installation and device regression

- [ ] Install the exact signed candidate without uninstalling anything; query package UID/signature and verify original apps/data and original Xiaomi HOME remain enabled.
- [ ] Direct-launch the app and exercise grid/Dock, BrowseHere, VLC, Xiaomi settings, exact HDMI routes, unsupported HW8 suppression, and input fallback behavior.
- [ ] Test system picker/import, wallpaper switching, none/default, dimming, restart persistence, and damaged/oversized-image fallback.
- [ ] Test the ordinary-app native preview path. If the public route is unavailable, record the concrete platform boundary and retain the working native HOME path.
- [ ] Exercise Home activation only through the standard resolver, perform the guarded restore rehearsal, then return the TV to the safest user-acceptance state.

## Task 6: Sanitized public repository and Release

- [ ] Add the real scanner and private sentinel test; export only the reviewed source/docs/workflow/tools into a fresh public history.
- [ ] Run source/history/APK/ZIP/certificate scans and remove or redact every private value; do not include internal reports, runs, private state, keys, or user images.
- [ ] Push the sanitized source to the dedicated public repository, lock the source commit, and run CI build/test/lint from that commit.
- [ ] Build/sign the final candidate from the locked public source, generate `release-manifest.json` and `SHA256SUMS.txt`, and create the required APK and InstallKit names.
- [ ] Create a prerelease tag/release without overwriting existing tags; upload the exact artifacts and verify direct download links.
- [ ] Download assets into a fresh directory, compare hashes/signatures, install the downloaded APK over the same package, and run final smoke checks.

## Task 7: Final evidence and handoff

- [ ] Write separate IMPLEMENTATION, LOCAL/CI TECHNICAL, DEVICE RUNTIME, HUMAN/VISUAL, and PUBLIC RELEASE statuses.
- [ ] Record OPEN ITEMS, BLOCKED ITEMS, PRODUCTION ITEMS, current TV state, and the concrete human acceptance checklist.
- [ ] Confirm no private values are in public artifacts or final links, and report any genuine platform limitation without inflating the completion claim.
