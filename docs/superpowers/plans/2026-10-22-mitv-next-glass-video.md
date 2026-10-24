# MiTV Next Glass Video Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the current TVHome implementation with a private, real video-wallpaper candidate, a unified wallpaper catalog, Android 11-compatible glass settings surfaces, and truthful device evidence while preserving the in-progress HOME/boot work.

**Architecture:** Keep the existing Java/XML launcher, SharedPreferences keys, ThemeProvider boundary, WallpaperPlayer ownership model, and original Xiaomi HOME enabled. Add catalog metadata and preview/UI behavior around the existing wallpaper paths; use the supplied MediaPlayer/TextureView, blur, lens, glass drawable, and dialog classes only where their API30-safe contracts fit. Private media stays outside public artifacts.

**Tech Stack:** Android Java/View, AndroidX RecyclerView, Gradle/AGP project, JDK 21 local toolchain, PowerShell/ADB, Python tests, supplied SDK34-compatible core classes.

**Spec:** `E:/MiTV-CleanYourTV/handoff/MiTV-Next-Glass-Video/TASK_NEXT.md`

## Global Constraints

- Preserve current HOME/boot/recovery changes, original Xiaomi HOME, user applications/data, signing identity, existing preference keys, Dock/order/layout behavior, and external system boundaries.
- Do not clear data, uninstall existing apps, silently enable accessibility, write arbitrary secure settings, add root/system signing, or publish private media/device evidence.
- Android 11/API30 must have a real blur result; do not rely on API31+ RenderEffect.
- Video must be one muted looping MediaPlayer, no audio focus, no completion-time player recreation, and release while settings/external app/screen saver/standby owns the foreground.
- ADB/automated evidence is separate from visual and human acceptance; incomplete gates remain candidate/partial.

## Review Focus

- Existing preferences and legacy `wallpapers/` files survive catalog migration; test malformed metadata and path traversal before writing production code.
- An imported file that already exists with the same length but a different SHA is replaced atomically; test hash readback and old-file preservation on failure.
- Preview and home playback never own two players or audio focus at once; test coordinator release and failure fallback.
- Settings blur never captures text/other apps and falls back to the poster without a black flash; test API30-compatible renderer boundaries.
- HOME helper/boot changes are not overwritten by the UI/video work; run the existing home contract and deployment tests after each integration slice.

### Task 1: Baseline, supplied-core inventory, and catalog contracts

**Files:**
- Create: `docs/superpowers/plans/2026-10-22-mitv-next-glass-video.md` (this plan), private evidence under `deliverables/next-glass-video-20261022/`.
- Modify: none until baseline is recorded.
- Test: existing Python and Android unit tests.

- [ ] Record current HEAD/status/diff, installed candidate version/signature, device state, and the supplied task archive hash without exposing private identifiers.
- [ ] Inspect current `MainActivity`, `WallpaperPlayer`, `VideoWallpaperImport`, `Config`, `ThemeProvider`, settings layout/activity, and the frozen HOME files.
- [ ] Add focused failing tests for catalog schema/path safety, stable video identity, and legacy preference preservation.
- [ ] Run the new tests and confirm they fail for missing catalog behavior, not due to a test typo.

### Task 2: Private video import and catalog persistence

**Files:**
- Create: `launcher/app/src/main/java/com/gathin/tvhome/WallpaperCatalog.java`, `launcher/app/src/main/java/com/gathin/tvhome/WallpaperCandidate.java`, tests for catalog/import behavior.
- Modify: `VideoWallpaperImport.java`, `VideoWallpaperPolicy.java`, `WallpaperImport.java`, `Config.java`, `ThemeFiles.java`, `ThemeProvider.java` only for additive compatibility.
- Test: catalog schema/path tests, SHA replacement/readback tests, existing wallpaper/import tests.

- [ ] Implement schema-1 metadata with bounded IDs, relative paths only, atomic writes, and scan fallback when metadata is corrupt.
- [ ] Register existing images plus the supplied 1080p30 “太空列车” candidate with title/type/poster/real dimensions/fps/duration/SHA; keep 4K metadata private for capability testing.
- [ ] Change equal-length existing-file handling to verify the complete SHA before accepting it; replace a corrupt target only after the temporary copy validates.
- [ ] Preserve `background_mode`, `video_wallpaper_path`, `wallpaper_choice`, and `wallpaper_dim`; image application must also set image mode.
- [ ] Run focused tests, then the full Python suite and Android unit tests.

### Task 3: API30 video lifecycle and background ownership

**Files:**
- Create/modify: `VideoWallpaperPlayer.java`, `WallpaperPlayer.java`, `BackgroundResourceCoordinator.java`, `MainActivity.java`, `activity_main.xml`.
- Add tests: player/coordinator and static source contract tests.

- [ ] Add the supplied player/core implementation through a pure background `FrameLayout` at root index 0, keeping UI/focus above it.
- [ ] Keep the poster visible until a real first frame/render-start callback, mute with `setVolume(0,0)`, loop one player, and release on pause/stop/destroy.
- [ ] Ensure failure falls back once to the previous image/poster without retry storms and that settings/external app/screen saver cannot leave a second player running.
- [ ] Run contract/unit tests and build a local candidate before device installation.

### Task 4: Unified glass settings and wallpaper gallery/preview

**Files:**
- Create: `ArgbBoxBlur.java`, `ScreenSpaceLens.java`, `GlassPanelDrawable.java`, `GlassDialog.java`, `SettingsBackdropRenderer.java`, gallery/preview layouts or focused Java views, and owned-UI inventory.
- Modify: `LauncherSettingsActivity.java`, `activity_launcher_settings.xml`, `styles.xml`, drawable/resources, relevant localized strings.
- Test: static AlertDialog gate, settings/catalog behavior tests, Android unit tests.

- [ ] Replace the single vertical settings page with the existing feature set arranged under six categories, preserving current IDs/values and DPAD focus memory.
- [ ] Use a one-shot wallpaper-layer snapshot + three-pass box blur + 40% scrim for settings; use static poster fallback and never instantiate a second video player.
- [ ] Use the supplied glass drawable/lens for focus/selected separation and replace self-owned `AlertDialog.Builder` surfaces with `GlassDialog`; retain only explicit external system activities.
- [ ] Build a shared image/video gallery with friendly names, poster, “动态” label, preview page, “设为壁纸”, “返回”, import progress/success/failure, cancel preservation, and atomic apply.
- [ ] Run the static gate and unit tests; verify the UI package builds before device work.

### Task 5: Private media deployment and real-device verification

**Files:**
- Modify/create: `Import-VideoWallpaper.ps1` only as needed for the protected provider path; private evidence reports and screenshots under `deliverables/next-glass-video-20261022/`.
- Test: install/readback and acceptance evidence.

- [ ] Build with the real available toolchain, sign with the existing key, verify package/version/certificate/hash, and install with `install -r` without clearing data.
- [ ] Import the supplied 1080p30 video and poster through the existing protected path; read back SHA and verify the TV gallery shows the candidate.
- [ ] Validate preview motion, apply/cancel persistence, image/video/default switching, background-only dimming, real foreground package/version, and resource release.
- [ ] Capture two home frames more than two seconds apart and inspect original PNGs; capture settings categories/gallery/preview/blur/dialog/error surfaces and inspect each at native size.
- [ ] Record real loop-boundary evidence, decoder/PSS/gfxinfo where supported, and mark unsupported 4K/30-minute/physical observations UNKNOWN rather than PASS.

### Task 6: Debloat boundary, packaging, and truthful handoff

**Files:**
- Create private: `EXECUTION_REPORT.md`, `DECISIONS.md`, `OWNED_UI_INVENTORY.md`, `VIDEO_LOOP_REPORT.md`, updated `ACCEPTANCE_NEXT.csv`, `REVIEW_CORE.zip`.
- Modify: only exact debloat/recovery scripts if device evidence supports the already-authorized 1/3/4/5/6/8 list.
- Test: public-release scanner, full tests, release manifest/hash/privacy checks.

- [ ] Execute only device-confirmed authorized components in reversible batches; preserve the Xiaomi app store and protected HOME/input/voice/install/media chains.
- [ ] Fill every matrix row with evidence class and a truthful result; do not call HOME/boot or visual acceptance complete without the corresponding device/visual proof.
- [ ] Produce a private candidate package and a public-safe package without the private video, poster, screenshots, device identifiers, or signing material; scan all tracked files, APK assets, ZIP entries, and CI scopes.
- [ ] Run the full available verification suite and leave the TV on the actual candidate desktop only after the requested device checks are complete.
