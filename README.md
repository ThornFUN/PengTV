# PengTV

English | [简体中文](README.zh-CN.md)

PengTV is an Android TV suite with three independently installable applications:

| Module | Package | Purpose |
|---|---|---|
| PENG Launcher | `com.peng.launcher` | A lightweight TV launcher with remote-control navigation and app management. |
| PENG Live | `com.peng.live.show` | An IPTV player that supports user-provided M3U and XMLTV sources. |
| PENG Movie | `com.peng.movie` | A configurable media client based on TVBox components. |

## Features

- Remote-first navigation and focused UI for TVs and set-top boxes.
- Launcher home screen with configurable app shortcuts, an all-apps page, Wi-Fi settings,
  wallpaper selection, and app management.
- IPTV playback with source import, XMLTV program-guide support, quality profiles, fallback
  routes, and local playback history.
- Android 4.4 / API 19 compatibility work for legacy Konka TV hardware, including 32-bit ARM
  packaging and IPv4-first networking.
- A local-network screen-casting prototype for compatible Android devices.

## Requirements

| Item | Requirement |
|---|---|
| JDK | 17 |
| Android SDK | API 35 for compilation |
| Gradle | Project wrapper (Gradle 8.11.1) |
| Target device | Android TV, TV box, or large-screen Android device |

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
