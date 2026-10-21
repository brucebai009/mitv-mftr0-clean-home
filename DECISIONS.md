# MiTV CleanYourTV — device decisions

Date: 2026-10-21 (Asia/Hong_Kong)

## Fixed baseline

- Upstream repository: `Gathin23/cleanyourtv`
- Required upstream commit: `d27bb0604c75b9b144fb278e6e5d0671074b25f9`
- Local vendor snapshot commit: `0513dc46ef1a30e869015146e27be561f9768368`; this is a vendor snapshot commit, not the upstream commit object.
- Required Android toolchain remains AGP 8.5.2, Gradle 8.9, compile/target SDK 34, min SDK 26, Java 17.

## Device evidence

- Model: `MiTV-MFTR0`; device: `freeguy`; Android 11 / API 30.
- Fingerprint: `xiaomi/freeguy/freeguy:11/RP1A.200720.011/OS2.0.5.0.RFTMATV:user/release-keys`.
- Original HOME: `com.mitv.tvhome/.MainActivityUserMode`; it remains enabled and was not replaced or disabled.
- The original HOME showed a PlayStation 5 source selected on HDMI3 with a central native preview panel.
- `dumpsys tv_input` recorded an active system-owned session on `com.mediatek.tvinput/.../HW7`, HDMI physical port 3, with PS5 CEC metadata.
- Two unchanged screenshots are evidence of a stable native panel only; they do not prove changing video frames.

## Native preview decision

The preview is an original Xiaomi HOME/source-panel experience backed by the system MediaTek TV Input chain. No ordinary-UID public API or embeddable preview surface has yet been proven. The implementation therefore preserves the stock HOME and does not disable any vendor package. Dynamic visual confirmation remains HUMAN_PENDING until the live HDMI image is changed and observed on the TV.

## Compatibility decision

The launcher uses an exact model + fingerprint profile. Vendor routes and extra package exposure are empty on all other firmware. The profile adds optional routes for the observed HDMI/tuner IDs, uses the observed Xiaomi settings activities, keeps the upstream TvInput fallback, and excludes only the observed non-physical `HW8` input. All routes are marked shell-verified only until an ordinary-app click is tested.

## Toolchain decision

The required API 34 platform and Build Tools 34 are not installed locally. Official Google repository access timed out. Gradle plugin resolution was confirmed through a local-only init script using the reachable Aliyun Google Maven mirror; this script is outside the product repository. No API 34 package was fabricated from API 37.
