# PengTV

English | [简体中文](README.zh-CN.md)

PengTV is an Android TV suite with five independently installable applications:

| Application | Package | Minimum Android version | Release artifact | Purpose |
|---|---|---|
| PENG Launcher | `com.peng.launcher` | Android 4.0 / API 14 | `PENG-Launcher-v0.1.0-release.apk` | A lightweight TV launcher with remote-control navigation, app shortcuts, wallpaper selection, Wi-Fi settings, and app management. |
| PENG Live | `com.peng.live.show` | Android 4.4 / API 19 | `PENG-Live-v0.1.0-release.apk` | An IPTV player for user-provided M3U and XMLTV sources, with program-guide previews, quality profiles, route fallback, and playback history. |
| PENG Movie | `com.github.tvbox.osc.tk` | Android 4.4 / API 19 | `PENG-Movie-API19-armeabi-v7a-release.apk` or `PENG-Movie-arm64-release.apk` | A configurable media client based on TVBox components. Use the `armeabi-v7a` build on 32-bit legacy TVs and the `arm64` build on 64-bit devices. |
| PENG Cast Sender | `com.peng.cast.sender` | Android 5.0 / API 21 | `PENG-Cast-Sender-v0.1.20-release.apk` | A source-device application that captures a permitted screen and microphone stream for local-network casting. |
| PENG Cast Receiver | `com.peng.cast.receiver` | Android 4.4 / API 19 | `PENG-Cast-Receiver-v0.1.29-release.apk` | A TV-oriented local-network cast receiver with QR pairing. Its Release APK embeds the signed PENG Cast Sender APK for convenient installation. |

Only install one PENG Movie APK variant on a device because both variants use the same application
ID. The legacy `PENG-Movie-API19-arm64-release.apk` is not a recommended release artifact.

## Release checksums

| Artifact | SHA-256 |
|---|---|
| `PENG-Launcher-v0.1.0-release.apk` | `6a7f45576b2e8f6123879f9b2ec29750f1c4ac9a3d956f6f59a46a660d644d0f` |
| `PENG-Live-v0.1.0-release.apk` | `77ad8e8ed79abc751466f16bc9209fc936cc13099441de802d1475e28136a88a` |
| `PENG-Movie-API19-armeabi-v7a-release.apk` | `e532f4e717d2ee3429e10bb8e502cf0ce8641972e1a071f5317665a869e66d98` |
| `PENG-Movie-arm64-release.apk` | `246cbb59b50680437ee14f29b2760bda861e426604f60c6aa60487ef29b9b7d4` |
| `PENG-Cast-Sender-v0.1.20-release.apk` | `b2e3e86b973a96db3954cbd21d30ea2df37370c67336a872e3ed894a0fc4a44b` |
| `PENG-Cast-Receiver-v0.1.29-release.apk` | `367c861c7b4b5ccce9d6ea5b47d795a754d389ba721fb2a7e997ed2411aca3a8` |

## Application details

### PENG Launcher

PENG Launcher is designed as a lightweight home screen for TVs and set-top boxes. It provides
two rows of configurable shortcuts, an all-apps page, remote-control focus navigation, system
Wi-Fi settings, local wallpaper selection, and basic app management.

### PENG Live

PENG Live is an IPTV application that does not replace the system launcher. Users can add M3U
channels and XMLTV programme-guide sources they are authorized to use. It supports quality
profiles, route fallback, recent playback restoration, remote shortcuts, and local-network phone
import while the import screen is open.

### PENG Movie

PENG Movie is a configurable media client built from TVBox components. Media and source
configuration are supplied by the user. The project includes Android 4.4 / API 19 compatibility
work for legacy Konka TV hardware, including 32-bit ARM packaging and IPv4-first networking.

### PENG Cast Sender and Receiver

PENG Cast Sender runs on the source Android device and requests the system media-projection and
microphone permissions before it transmits. PENG Cast Receiver runs on Android TV or another
large-screen device, presents the received stream, and supports QR pairing on the same local
network. The receiver's Release APK embeds the same signed sender Release APK that is published
as a separate asset.

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
