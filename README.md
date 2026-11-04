# PengTV

English | [简体中文](README.zh-CN.md)

PengTV is an Android TV suite with five independently installable applications.
All v0.1.0 Release assets are below 25 MiB.

## Applications

### PENG Launcher

- **Package:** `com.peng.launcher`
- **Minimum Android version:** Android 4.0 / API 14
- **Release APK:** `PENG-Launcher-v0.1.0-release.apk`
- **Release size:** 0.92 MiB (968,771 bytes)
- **Purpose:** A lightweight TV launcher with remote-control navigation, app shortcuts, wallpaper
  selection, Wi-Fi settings, and app management.

### PENG Live

- **Package:** `com.peng.live.show`
- **Minimum Android version:** Android 4.4 / API 19
- **Release APK:** `PENG-Live-v0.1.0-release.apk`
- **Release size:** 2.86 MiB (3,001,345 bytes)
- **Purpose:** An IPTV player for user-provided M3U and XMLTV sources, with programme-guide
  previews, quality profiles, route fallback, and playback history.

### PENG Movie

- **Package:** `com.github.tvbox.osc.tk`
- **Minimum Android version:** Android 4.4 / API 19
- **Release APKs:** `PENG-Movie-API19-armeabi-v7a-release.apk` for 32-bit legacy TVs, or
  `PENG-Movie-arm64-release.apk` for 64-bit devices.
- **Release sizes:** 21.78 MiB (22,838,280 bytes) for `armeabi-v7a`; 23.19 MiB
  (24,318,687 bytes) for `arm64`.
- **Purpose:** A configurable media client based on TVBox components.

Only install one PENG Movie APK variant on a device because both variants use the same application
ID. The legacy `PENG-Movie-API19-arm64-release.apk` is not a recommended release artifact.

### PENG Cast Sender

- **Package:** `com.peng.cast.sender`
- **Minimum Android version:** Android 5.0 / API 21
- **Release APK:** `PENG-Cast-Sender-v0.1.20-release.apk`
- **Release size:** 0.98 MiB (1,028,449 bytes)
- **Purpose:** A source-device application that captures a permitted screen and microphone stream
  for local-network casting.

### PENG Cast Receiver

- **Package:** `com.peng.cast.receiver`
- **Minimum Android version:** Android 4.4 / API 19
- **Release APK:** `PENG-Cast-Receiver-v0.1.29-release.apk`
- **Release size:** 2.15 MiB (2,252,255 bytes)
- **Purpose:** A TV-oriented local-network cast receiver with QR pairing. Its Release APK embeds
  the signed PENG Cast Sender APK for convenient installation.

## Release checksums

- `PENG-Launcher-v0.1.0-release.apk`:
  `6a7f45576b2e8f6123879f9b2ec29750f1c4ac9a3d956f6f59a46a660d644d0f`
- `PENG-Live-v0.1.0-release.apk`:
  `77ad8e8ed79abc751466f16bc9209fc936cc13099441de802d1475e28136a88a`
- `PENG-Movie-API19-armeabi-v7a-release.apk`:
  `e532f4e717d2ee3429e10bb8e502cf0ce8641972e1a071f5317665a869e66d98`
- `PENG-Movie-arm64-release.apk`:
  `246cbb59b50680437ee14f29b2760bda861e426604f60c6aa60487ef29b9b7d4`
- `PENG-Cast-Sender-v0.1.20-release.apk`:
  `b2e3e86b973a96db3954cbd21d30ea2df37370c67336a872e3ed894a0fc4a44b`
- `PENG-Cast-Receiver-v0.1.29-release.apk`:
  `367c861c7b4b5ccce9d6ea5b47d795a754d389ba721fb2a7e997ed2411aca3a8`

## Requirements

- **JDK:** 17
- **Android SDK:** API 35 for compilation
- **Gradle:** Project wrapper (Gradle 8.11.1)
- **Target device:** Android TV, TV box, or large-screen Android device

The launcher supports Android 4.0 / API 14 and later. PENG Live and the default Java flavor of
PENG Movie support Android 4.4 / API 19 and later.

## Build

```bash
git clone https://github.com/ThornFUN/PengTV.git
cd PengTV
./gradlew assembleDebug
```

Common module commands:

```bash
./gradlew :launcher:assembleDebug
./gradlew :live-show:assembleDebug
./gradlew :movie:assembleArm64GenericNormalDebug
```

Signing credentials are not committed. Copy `keystore.properties.example` to
`keystore.properties`, provide local signing values, and run `./gradlew assembleRelease` to
create signed release builds.

## Source and content policy

PengTV does not provide video content or bundled third-party media sources. Users are responsible
for supplying sources and media they are authorized to use.

## License

PengTV is distributed under the [MIT License](LICENSE). Third-party components retain their
respective licenses; see their bundled notices and source files.
