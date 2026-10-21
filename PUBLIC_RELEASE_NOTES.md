# TVHome 1.0.0-rc.1

This is a prerelease candidate for manual acceptance on the recorded MiTV-MFTR0 profile.

Technical scope:

- Android 11/API 30 runtime target, app targetSdk 34, minSdk 26.
- Original TV HOME remains enabled.
- Source routes are profile-gated and fall back when the model/fingerprint does not match.
- Wallpaper import is implemented through the system document picker and the desktop ADB helper.
- The native preview panel is preserved. Ordinary-UID access and dynamic HDMI behavior are reported separately from static evidence.

This release does not claim universal TV compatibility, dynamic preview success from screenshots, or changes to vendor firmware. The exact device runtime and human acceptance statuses are included in `release-manifest.json`.
