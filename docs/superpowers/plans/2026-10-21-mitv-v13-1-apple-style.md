# Plan: MiTV V13.1 Apple-style TVHome release candidate

## Spec and scope

- Authority: the local V13.1 task bundle and its referenced feature, UI, matrix, audit, and baseline documents.
- Worktree: `repository root` on `release/v13.2`, preserving the existing implementation and all user data.
- Device target: MiTV-MFTR0, Android 11/API 30, user 0, ARM32; current ADB audit has no online device, so physical acceptance remains explicitly pending until a device is connected.
- Product boundary: extend existing Java/XML/config/provider/installer architecture. Do not replace the original Xiaomi HOME, disable or uninstall original apps, clear user data, write unknown secure settings, use root/remount/system signing/Device Owner/Shizuku/accessibility takeover, fake HDMI/YouTube evidence, or publish private identifiers and logs.
- Release identity: same application ID and signing certificate, versionCode greater than 7, new candidate name and manifest; never overwrite `v1.0.0-rc.1-tools.1`.

## Expected deliverables

- Source changes and JVM tests in the existing Android module.
- Private run evidence outside the public export under a local timestamped deliverables directory.
- `CAPABILITY_AUDIT.md`, `capabilities.json`, `HOME_BOOT_REPORT.md`, `UI_COMPARISON.md`, `PREVIEW_CAPABILITY_REPORT.md`, `DEBLOAT_AUDIT.md`, `TEST_FAILURE_CLASSIFICATION.md`, and `V13_1_EXECUTION_REPORT.md` with evidence-class labels.
- Versioned unsigned build plus locally signed APK when the signing key is available, a manifest-driven InstallKit, SHA-256 records, and a scrubbed public source/release staging directory. Public publication is a separate side effect and is not performed without an explicit user publish authorization.

## Global verification rules

- Every new behavior starts with a failing JVM test, followed by the smallest compatible implementation and the relevant module/full test suite.
- Report build, JVM test, synthetic ADB, physical TV, and user acceptance as separate evidence classes.
- A device-dependent conclusion is one of `SUPPORTED_VERIFIED`, `API_CONNECTED_VISUAL_PENDING`, `BLOCKED_PERMISSION`, `BLOCKED_VENDOR`, `NO_ACTIVE_SOURCE`, `COMMAND_DENIED`, `ROLE_UNAVAILABLE`, `HOME_NOT_ELIGIBLE`, `RESOLVER_MISMATCH`, `FOREGROUND_TIMEOUT`, or `SUCCESS`; no device result is inferred from source or a missing ADB connection.

## Tasks

### 1. Baseline audit and evidence contract

1. Record HEAD, branch, clean status, existing version/hash, public baseline relation, Android toolchain, and current ADB availability in a private timestamped run directory.
2. Inspect the current HOME resolver, exported HOME eligibility, accessibility declaration, provider permissions, foreground parser, wallpaper/settings persistence, and existing installer/restore scripts.
3. Re-run all repository tests that are possible on Windows and classify every pre-existing full-suite failure into actionable categories; do not hide or relabel failures.
4. Write the required audit/report skeletons from actual evidence, redacting serials, network identifiers, screenshots and private paths from public-facing material.

### 2. Home gesture contract and safe helper boundary

1. Add failing pure JVM tests for the exact single/double/triple timing, long/repeat/cancel, duplicate delivery, app-launch, sleep/wake and post-stock cases from the task.
2. Implement a small pure `HomeGestureController` with injectable clock and explicit `NEW_HOME`, `STOCK_HOME`, and `NONE` results; keep the existing V12.1 parser and non-Home key mappings unchanged.
3. Add only a user-manually-enabled `home_only` helper path. It must not read windows/screens, inject gestures, draw overlays, write secure settings, or consume non-Home keys. Surface the task’s exact helper-off/unsupported copy and verified Settings route.
4. Add regression coverage proving the existing `HomeKeyService` mappings and the original HOME restore path remain intact.

### 3. Recovery, diagnostics and boot evidence

1. Add failing tests for a narrow recovery contract: caller permission, own helper/boot/reset flags only, no arbitrary settings or Intent execution, non-exported broadcast behavior, and old rc.1 snapshot compatibility.
2. Extend the existing provider/installer/restore architecture with manifest-driven diagnostics and a `Deploy-TVHome.ps1` thin orchestrator; never silently report default HOME activation success.
3. Add device-run capture for resolver-before/after, `set-home-activity` stdout/stderr/exit code, resumed/currentFocus/focusedApp, and cold/standby/auto-power-off cases when ADB is online.

### 4. Apple-style HOME surface using existing views and persistence

1. Add failing tests for runtime-size layout policy, dock slot preservation/add-remove-reorder/save-cancel, exact settings copy, and backward-compatible preference/config decoding.
2. Extend the existing `MainActivity`, XML layouts, `GlassView`, `Config`, `Order`, and settings activity rather than replacing the app architecture. Use runtime display metrics and the existing hardware-cached glass path; no API31+ blur/shader APIs.
3. Remove old giant clock/greeting/hero emphasis from the V13.1 default surface, add the compact status row and centered bottom dock policy, retain actual installed app labels/icons/banners, and keep all old settings keys readable.
4. Add the required settings categories and verified routes for Home, background/motion, screensaver/idle, signal source, TV system settings, and applications.

### 5. Video wallpaper, idle coordinator and screensaver-safe lifecycle

1. Add failing tests for MP4/H.264/SDR/size/duration/fps/bytes validation, safe names, SHA-256 readback, atomic selection, fallback, and coordinator exclusivity.
2. Implement an additive private video-wallpaper store/import validator and integrate it into `WallpaperPlayer` with `MediaPlayer`/`TextureView`, mute/loop/no audio focus, async preparation, pause/release on Home exit/stock/screen saver/standby, and image fallback on every failure.
3. Keep the original system Dream and timeout untouched. Any TVHome idle visual is opt-in, Home-only, and disabled by default. Provide the local 10-second generated demo only if the local toolchain supports it, never copy private reference assets.

### 6. Signal-source, preview, YouTube and debloat audits

1. Add source-level/unit coverage for ordinary-UID `TvView` passthrough URI construction and the required conclusion classes; no hidden APIs or shell-faked preview.
2. Implement only the capability probe and clear fallback route; report preview as pending/blocked unless an actual callback and visual screen are observed.
3. Audit installed YouTube candidates without login and without rehosting third-party APKs. Record explicit unavailable/source-pending status if no upstream ARM32 asset is locally available.
4. Produce a keep/candidate/unknown debloat audit; perform no package mutation unless a verified, reversible whitelist and user-authorized device run exist.

### 7. Build, signed candidate, regression and package

1. Run JDK17/AGP8.5.2/Gradle8.9/SDK34 checks, unit tests, lint and release assemble. Classify any failure and fix only regressions introduced by this branch.
2. Set versionCode above 7 and a V13.1 candidate version while preserving the same package and signing requirements. Sign locally only if the existing trusted key is present; otherwise deliver an explicitly unsigned candidate and state the blocker.
3. Generate release manifest, SHA-256, installer/media-import/Home diagnostics/trusted-restore package, and scrubbed public export. Validate that no private serial/IP/MAC/SSID/screenshots/ADB logs/keys/user assets are included.

### 8. Final review and handoff

1. Run the full available test/build/privacy suite and inspect the final diff and generated artifacts as a separate review pass.
2. If a device becomes available, install without clearing data, run the acceptance matrix, collect real screenshots and boot/foreground evidence, and restore the original HOME after testing unless the task’s final TVHome state is actually verified.
3. Publish only after explicit authorization; otherwise leave a ready-to-publish local package with exact paths and hashes. Final status must use one of the task’s allowed statuses and list blockers and human acceptance steps.

## Review focus

- Same-cert/version compatibility and no data loss.
- Whether Home behavior is truly physical-HOME/boot verified versus only source/synthetic evidence.
- Provider/installer security boundary and manifest/hash coupling.
- Video lifecycle leaks, audio focus, fallback and screen-saver/standby release.
- Public-export privacy scan and the distinction between source, build, ADB, physical and user evidence.
