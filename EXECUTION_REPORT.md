# WONJEAN / MiTV CleanYourTV execution report

Date: 2026-10-21 (Asia/Hong_Kong)

## IMPLEMENTATION

- Repository root: `E:\MiTV-CleanYourTV\repo`
- Local branch: `bruce/mitv-clean-home-v1`
- Upstream archive: fixed commit `d27bb0604c75b9b144fb278e6e5d0671074b25f9`
- Product version: `2.4-bruce.1`, versionCode `6`.
- The implementation is an additive exact-device profile. It does not change the upstream package identity, uninstall applications, clear application data, disable the factory launcher, or write system partitions.

## LOCAL TECHNICAL

- Static profile tests: `python -m pytest tests/test_mitv_profile.py -q` — PASS, 3 passed.
- Gradle/plugin task discovery with the fixed AGP/Gradle versions — PASS using the local toolchain mirror init script.
- Required release/unit-test build — BLOCKED locally because `E:\Android\Sdk` has no `platforms;android-34` or `build-tools;34.0.0`. An offline Gradle attempt failed deterministically with `Failed to find target with hash string 'android-34'`; no substitute API was copied or renamed.
- No install was performed before a conforming API 34 build existed.

## DEVICE RUNTIME

- ADB device: `192.168.5.175:5555`, authorized after the user confirmed the TV prompt.
- Device: `MiTV-MFTR0`, `freeguy`, Android 11/API 30, fingerprint recorded in `DECISIONS.md`.
- BrowseHere `com.tcl.browser` and VLC `org.videolan.vlc` were present and enabled before changes; no app data was changed.
- Original HOME and the system TV Input chain were observed. The new launcher package was absent during baseline capture and was not installed while the conforming build was unavailable.
- `dumpsys package` shows `com.mitv.tvhome`, `com.mediatek.tvinput` and `com.xiaomi.mitv.tvplayer` running as system UID 1000 with privileged TV-input/overlay capabilities; the product launcher is an ordinary application and does not request those privileges. This is why the profile keeps the native panel in the stock HOME and treats vendor activity routes as shell-verified only.

## HUMAN / VISUAL

- PASS: original HOME visibly displayed the HDMI source list and the central PS5 preview panel.
- NOT PROVEN: the two captured screenshots were byte-identical, so screenshots alone cannot establish changing HDMI frames.
- HUMAN_PENDING: change the PS5 screen while the native panel is visible and confirm the panel changes live on the TV.

## DAILY USE

- Pending installation and ordinary-UID route regression after API 34 is available.
- Stock HOME remains the recovery baseline. No disable/uninstall command was run.
