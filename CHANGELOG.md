# Changelog

## v13.18.1 — 2026-11-20

- Route HDMI-CEC device selections through their reported physical parent on exact supported device profiles; preserve existing routes and generic fallbacks.
- Add full-card pearl/chrome artwork for Apple Music TV in the shared Dock/library binding path.
- Preserve launcher signing identity, HOME setup, wallpaper and user settings during cover installation.
- Add 12 input-route unit tests and document the actual device checks and untested power scenarios.

## 2.4.0 — 2026-10-21

- Added an exact Xiaomi MiTV-MFTR0 / OS2.0.5.0.RFTMATV device profile.
- Added optional observed HDMI, AV, ATV, DTMB and Xiaomi Settings routes.
- Kept the upstream TvInput URI fallback for unprofiled devices.
- Added ordinary launcher discovery for verified profile packages without changing other app discovery rules.
- Pinned the dock to the installed BrowseHere and VLC package IDs.
- Removed the unusable Live TV package tile and module-card prompt for this deployment profile.
- Preserved the stock Xiaomi HOME and did not claim native preview embedding or dynamic-video success.
