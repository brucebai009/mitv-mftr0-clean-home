# MiTV FINAL-20261022 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a same-signed, independently deployable TVHome candidate from the current V13.1 baseline, with the final Apple-style/Rain city presentation, truthful Home/input/video/content capability states, tested install/restore tooling, and separated private/public evidence.

**Architecture:** Extend the existing Java TVHome architecture and data/config paths. Normalize the release manifest once and make every PowerShell installer/import/restore path consume that normalized contract. Use the task package's Rain city MPKG/PNG only in a private media workspace; the TV player remains the existing MediaPlayer/TextureView path with explicit 1080p/4K capability results and safe fallback.

**Tech Stack:** Android Java, AndroidX RecyclerView/DynamicAnimation, Gradle/AGP 8.5.2, JDK 17, PowerShell, Python tests, ADB, existing TVHome provider/service contracts.

**Spec:** `E:/MiTV-CleanYourTV/handoff/MiTV-FINAL-20261022/CODEX_TASK_FINAL.md`

## Global Constraints

- Preserve package `com.gathin.tvhome`, original Xiaomi HOME, MediaTek input chain, user apps/data, signing certificate, and existing Order/DockSlots/config models.
- Do not uninstall, clear data, root, remount, system-sign, change global animation scale, silently enable accessibility, or scan/publish device identity.
- The original Xiaomi HOME remains enabled; HomeOnly helper is user-enabled only and must not read windows/text or auto-click authorization.
- Final manifest is schemaVersion 2 with `packageName`, `apk.fileName`, `apk.sha256`, `apk.sizeBytes`, certificate, source commit, real toolchain, and acceptance status.
- Build must use real SDK34 or a real SDK34 CI run; API37 metadata substitution is not acceptable for final release.
- Private Rain city media/evidence and device data never enter public source, Release, InstallKit, or public APK.
- Acceptance matrix distinguishes IMPLEMENTATION, LOCAL TECHNICAL, DEVICE, VISUAL/HUMAN, and PRODUCTION evidence; no user-acceptance claim is inferred from ADB-only evidence.

## Review Focus

- Legacy V13.1 preferences must migrate idempotently to the new first-screen geometry without deleting media or fixed-app data; test upgrade-like config fixtures.
- A malformed/old/partial manifest must fail closed before any device mutation; test missing hash, wrong filename, wrong package/version/certificate, unknown schema, and empty InstallKit.
- Home helper and recovery must not create an auto-restore loop or alter unrelated accessibility services; test allow-list and exact state transitions.
- HDMI enumeration, vendor full-screen routing, and ordinary-UID TvView preview remain separate classifications; test each path and never mark a source frame verified from connection alone.
- A rejected 4K/video import must preserve the previous wallpaper and never leave a second player/locked audio focus; test failure and lifecycle release.

### Task 1: Baseline, inventory, and evidence ledger

**Files:**
- Create: `SCREEN_INVENTORY.md`, `CURRENT_STATE.md`, private acceptance evidence under the final deliverable directory.
- Modify: none in production source.
- Test: existing read-only repo/device/build probes and screenshot/PNG validation.

- [ ] Record actual HEAD/status, installed package/version/signature, foreground, HOME resolver, helper state, display/window metrics, and original screenshots without exposing device identity.
- [ ] Open the task references and existing physical screenshots, compare the first screen and all reachable TVHome-owned surfaces, and write page ownership/entry/geometry/style/issue rows.
- [ ] Record the known API37 fallback as historical evidence only; do not use it to certify the final artifact.
- [ ] Create the private final acceptance directory and a ledger of evidence source (`ADB_KEYEVENT`, `PHYSICAL_REMOTE`, `ARTIFACT_CONFIRMED`, `USER_CONFIRMED`, or `UNKNOWN`).

### Task 2: Manifest contract and deployment/recovery tooling

**Files:**
- Modify: `release-manifest.json` generator/fixtures, `Install-TVHome.ps1`, `Deploy-TVHome.ps1`, `Restore-OriginalHome.ps1`, `Import-Wallpaper.ps1`, `Import-VideoWallpaper.ps1`.
- Create: `Restore-DisabledContent.ps1`, `Restore-TVHome-Changes.ps1`, manifest parser/test fixtures and behavior tests.
- Test: `tests/test_product_release_contract.py`, `tests/test_deployment_scripts.py`, new manifest/recovery behavior tests.

- [ ] Write failing tests for schemaVersion 2 normalization, strict hash/filename/package/version/certificate checks, explicit `-ApkPath`, old-format migration, and fail-closed device protection.
- [ ] Implement one typed manifest normalization path and route installer/deploy/import/restore validation through it; preserve `install -r` and snapshot checks.
- [ ] Write failing tests for Native/Helper/LaunchOnly mode separation and RecoveryProvider allow-list calls.
- [ ] Implement the two restore scopes and journaled, exact-state content restore without `pm enable`-all or Home/data mutation.
- [ ] Run targeted tests, then the existing deployment/privacy tests; commit the contract/tooling slice.

### Task 3: Unified TVHome visual structure and navigation

**Files:**
- Modify: `MainActivity.java`, `AppAdapter.java`, `DockDecoration.java`, `GlassView.java`, `LauncherSettingsActivity.java`, `HomeSettingsActivity.java`, `QuickSettingsActivity.java`, `activity_main.xml`, `activity_launcher_settings.xml`, `popup_inputs.xml`, `popup_cards.xml`, relevant `layout-iw`, `styles.xml`, `config.xml`, strings/drawables/animations.
- Create/modify: shared glass tokens/components and `AppleLayoutPolicy` tests.
- Test: new geometry/navigation/UI contract tests plus `:app:testReleaseUnitTest`.

- [ ] Write failing geometry tests for 4/5/6/7 columns, 16:10 cards, safe rect, focus reserve, full captions, and no default second-row exposure.
- [ ] Remove old left large clock/greeting/date layout occupancy in Apple mode and render one compact state pill group at the top-right.
- [ ] Make the first screen a full common-app dock only; Down enters a complete library and Back restores dock focus/scroll memory.
- [ ] Apply one glass surface/focus/selected-state system to every TVHome-owned page and sheet, with the canonical Chinese copy and no developer labels.
- [ ] Run focused JVM tests and build a runnable candidate before moving to the next screen.

### Task 4: Rain city media pipeline and 1080p/4K policy

**Files:**
- Create private-only: `wallpaper-work/Rain-city/*`, `WALLPAPER_ACCEPTANCE.md`, capture/transcode metadata and media hashes.
- Modify: `VideoWallpaperImport.java`, `VideoWallpaperPolicy.java`, `VideoWallpaperPlayer.java`, `WallpaperPlayer.java`, `BackgroundResourceCoordinator.java`, `LauncherSettingsActivity.java`, `Config.java`, related tests and import script.

- [ ] Verify the task MPKG hash and inspect it once with the supplied standard-library tool; do not overwrite the source or treat the PNG/preview as a video.
- [ ] Locate a legal local Wallpaper Engine installation/project once, open only a task-created independent window, and capture actual Rain city motion if the environment supports it. If blocked, record the exact blocker and continue with static PNG plus technical player tests.
- [ ] Write failing policy tests for 1080p compatibility, 4K hardware capability classification, size/fps/codec limits, rejected input preserving the old background, and player/resource exclusivity.
- [ ] Implement explicit 1080p/4K labels and safe fallback, import the private Rain city static/dynamic result, and never put user media in public artifacts.
- [ ] Validate at least three private loops where a dynamic asset exists, or mark the original dynamic target blocked and keep the stable static PNG final fallback.

### Task 5: Home, boot, input preview, and native boundary behavior

**Files:**
- Modify: `HomeGestureController.java`, `HomeOnlyAccessibilityService.java`, `RecoveryProvider.java`, `HdmiPreviewProbe.java`, `MainActivity.java`, `DeviceProfile.java`, manifest/service resources, tests and private reports.

- [ ] Write failing tests for immediate single Home, bounded three-press behavior, repeat/cancel/non-Home interruption, and recovery state clearing.
- [ ] Implement only the minimal lifecycle/permission-safe changes; keep the original HOME enabled and keep the helper manual-only.
- [ ] Measure and classify source enumeration, vendor full-screen handoff, and ordinary TvView preview independently; only a confirmed frame can enable an in-home preview card.
- [ ] Add truthful UI states for “尚未验证” versus “此电视暂不支持在桌面内预览” and provide the real Xiaomi fallback entry.
- [ ] Exercise ADB-keyevent paths separately from any user physical-remote evidence and record the distinction in the matrix.

### Task 6: Device content audit and reversible debloat

**Files:**
- Create private: `DEBLOAT_AUDIT.md`, `CONTENT_DISABLE_PLAN.json`, `CONTENT_CHANGE_JOURNAL.json`, `VOICE_ACCEPTANCE.md`, protection snapshot/plan files.
- Modify: debloat/recovery helpers and tests only where a real behavior gap is found.

- [ ] Capture the exact device package/component/service/role/input/screen-saver baseline and locate the real XiaoAI assistant/voice dependencies without guessing package names.
- [ ] Write failing tests for protected-package/component refusal, exact enabled-state restoration, batch rollback, empty/unknown plan refusal, and no user-app/data mutation.
- [ ] Execute only evidence-confirmed independent content components in batches of 1–5, read back each state, and immediately restore a failed batch.
- [ ] Regress TVHome launch, original HOME/input preview, installed apps/data, networking, screensaver, and XiaoAI/voice as far as the user’s presence permits; mark physical voice as HUMAN REVIEW when not available.

### Task 7: Real build, install, private package, and recovery regression

**Files:**
- Modify: CI workflow only if needed for real SDK34, build metadata, package scripts, README/manifest generation.
- Create: `deliverables/final-<timestamp>/public-release/*` and `private-acceptance/*`.

- [ ] Obtain/verify real `platforms/android-34` and `build-tools/34.0.0`, or run the existing fixed GitHub Actions SDK34 workflow; do not certify the API37 substitute.
- [ ] Run `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, privacy/deployment tests, and Linux/Bash classification for the prior 64 failures.
- [ ] Sign exactly one APK with the existing key, verify v2/v3, package/version/certificate, and install it with `install -r` without clearing data.
- [ ] From a newly extracted copy of the InstallKit, run install/import/Helper documentation and restore paths using only package-contained inputs; test Helper-enabled recovery when manually authorized, otherwise mark it pending.
- [ ] Return to the selected new TVHome mode after recovery and leave the final desktop visible and stable for the requested observation window.

### Task 8: Matrix, release audit, and final handoff

**Files:**
- Create/modify: all required files under `private-acceptance/`, `public-release/`, `ACCEPTANCE_MATRIX.csv`, final reports and SHA files.

- [ ] Fill every matrix row with one allowed result and concrete evidence/reason; do not use source/entry/no-crash evidence as functional PASS.
- [ ] Scan source/history/public assets for device address, serial, SSID, MAC, user media, screenshots, recordings, keys, private paths, and third-party APKs.
- [ ] Build one public source/install package with one main signed APK, manifest, SHA, Chinese README and exact limitations; keep private media/evidence separate.
- [ ] Publish only after a fresh public scan and re-download/hash/install verification; use a new tag/release and mark partial/blockers honestly.
- [ ] Perform a final self-review against the spec, then leave the television on the final TVHome desktop for human acceptance.
