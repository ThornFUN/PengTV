# PengTV

English | [简体中文](README.zh-CN.md)

PengTV is an Android TV suite with three independently installable applications:

| Application | Package | Minimum Android version | Release artifact | Purpose |
|---|---|---|
| PENG Launcher | `com.peng.launcher` | Android 4.0 / API 14 | `PENG-Launcher-v0.1.0-release.apk` | A lightweight TV launcher with remote-control navigation, app shortcuts, wallpaper selection, Wi-Fi settings, and app management. |
| PENG TV | `com.peng.video` | Android 4.4 / API 19 | `PENG-TV-v0.1.0-release.apk` | An IPTV player for user-provided M3U and XMLTV sources, with program-guide previews, quality profiles, route fallback, and playback history. |
| PENG Movie | `com.github.tvbox.osc.tk` | Android 4.4 / API 19 | `PENG-Movie-API19-armeabi-v7a-release.apk` or `PENG-Movie-arm64-release.apk` | A configurable media client based on TVBox components. Use the `armeabi-v7a` build on 32-bit legacy TVs and the `arm64` build on 64-bit devices. |

Only install one PENG Movie APK variant on a device because both variants use the same application
ID. The legacy `PENG-Movie-API19-arm64-release.apk` is not a recommended release artifact.

## Release checksums

| Artifact | SHA-256 |
|---|---|
| `PENG-Launcher-v0.1.0-release.apk` | `6a7f45576b2e8f6123879f9b2ec29750f1c4ac9a3d956f6f59a46a660d644d0f` |
| `PENG-TV-v0.1.0-release.apk` | `f4fd2b8881020852621d1f55e395fe406738b7593a3f81181955d9a5114b070a` |
| `PENG-Movie-API19-armeabi-v7a-release.apk` | `e532f4e717d2ee3429e10bb8e502cf0ce8641972e1a071f5317665a869e66d98` |
| `PENG-Movie-arm64-release.apk` | `246cbb59b50680437ee14f29b2760bda861e426604f60c6aa60487ef29b9b7d4` |

## Application details

### PENG Launcher

PENG Launcher is designed as a lightweight home screen for TVs and set-top boxes. It provides
two rows of configurable shortcuts, an all-apps page, remote-control focus navigation, system
Wi-Fi settings, local wallpaper selection, and basic app management.

### PENG TV

PENG TV is an IPTV application that does not replace the system launcher. Users can add M3U
channels and XMLTV programme-guide sources they are authorized to use. It supports quality
profiles, route fallback, recent playback restoration, remote shortcuts, and local-network phone
import while the import screen is open.

### PENG Movie

PENG Movie is a configurable media client built from TVBox components. Media and source
configuration are supplied by the user. The project includes Android 4.4 / API 19 compatibility
work for legacy Konka TV hardware, including 32-bit ARM packaging and IPv4-first networking.

### Screen casting prototype

The repository also includes a local-network screen-casting prototype for compatible Android
devices. It is an experimental component and is not included in the v0.1.0 release assets.

## Requirements

| Item | Requirement |
|---|---|
| JDK | 17 |
| Android SDK | API 35 for compilation |
| Gradle | Project wrapper (Gradle 8.11.1) |
| Target device | Android TV, TV box, or large-screen Android device |

The launcher supports Android 4.0 / API 14 and later. PENG TV and the default Java flavor of
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
