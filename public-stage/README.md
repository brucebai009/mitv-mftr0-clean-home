# TVHome for MiTV-MFTR0

TVHome is an independent, plain-Java Android TV launcher candidate. It keeps the original TV desktop enabled and uses standard Android intents and ADB only for setup and testing. It is not an official Xiaomi product and does not require root, system signing, accessibility takeover, or a device-owner role.

## What is included

- Four-column remote-friendly launcher with an application grid and a small dock.
- Explicit source routes for the supported MiTV-MFTR0 firmware profile, with safe fallback when the profile does not match.
- TV settings shortcuts and ordinary application launch behavior.
- User wallpaper import through the Android document picker, with a desktop ADB import fallback.
- 0–70% background dimming in 10% steps, defaulting to 30%.
- Guarded install and restore scripts that snapshot the original HOME resolver and read back the result.

The native source preview panel remains owned by the original TV software. The launcher does not claim that a static screenshot proves dynamic HDMI preview. See the release notes for the exact candidate evidence and human acceptance items.

## Build

The fixed toolchain is JDK 17, AGP 8.5.2, Gradle 8.9, Android SDK Platform 34, Build Tools 34.0.0, and minSdk 26. Run the build from `launcher`:

```text
./gradlew testReleaseUnitTest lintRelease assembleRelease
```

Release output is intentionally unsigned. Sign it on the target computer with a private release key; never commit a keystore or password.

## Install

Use the Release InstallKit and an explicitly supplied ADB serial:

```text
Install-TVHome.ps1 -Serial <TV_IP:PORT> -ApkPath .\TVHome-MFTR0-1.0.0-rc.1.apk
```

The default action installs and launches TVHome. Add `-ActivateHome` only after the direct launch works. The scripts never uninstall applications, clear application data, disable the original desktop, or modify TV image, audio, network, CEC, or input-method settings.

## Compatibility

The exact profile currently targets model `MiTV-MFTR0` with the public firmware fingerprint recorded in the profile. On another device the exact vendor routes stay disabled and the normal Android fallback remains available.

## Provenance

This is an independent derivative of the public CleanYourTV project. See `UPSTREAM.md` and `LICENSE` for attribution and licensing.
