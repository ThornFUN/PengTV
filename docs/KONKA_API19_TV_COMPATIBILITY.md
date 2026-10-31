# 康佳电视 API 19 兼容性档案

本文记录 PENG影视在当前康佳电视上的已知环境、故障、修复和待验证项，供后续排查使用。除特别标记为“待验证”的内容外，结论均来自电视 logcat 或已构建 APK 的检查结果。

## 设备与运行环境

| 项目 | 已知值 | 证据/说明 |
|---|---|---|
| 电视品牌 | 康佳（Konka） | 系统包名、日志和设备信息 |
| 设备代号 | `konka_6a648` | 已知设备信息 |
| 系统版本 | Android 4.4.4 | API 19 设备 |
| Android API | 19 | Dalvik 运行时 |
| 分辨率与形态 | `1920x1080`、横屏电视 | 电视日志运行时配置 |
| 应用运行 ABI | 32 位 ARM 进程 | logcat 的 native library path 为 `/data/app-lib/...:/system/lib/...`；不是 `/system/lib64` |
| 局域网 IP | `192.168.31.239` | 用户提供；仅在同一局域网时可通过 ADB 网络连接 |
| PENG影视包名 | `com.github.tvbox.osc.tk` | PENG影视 `upstream-box` 模块 |
| PENG 桌面包名 | `com.peng.launcher` | 当前电视已安装的桌面应用 |

> **ABI 规则：** 本机虽然可能被外部信息标记为 arm64 平台，但 PENG影视实际运行在 32 位进程。凡是需要 JNI 的功能必须使用 `armeabi-v7a` APK；仅含 `arm64-v8a` JNI 库的 APK 不能在该进程加载 IJK、QuickJS 等自身 native 库。

## 当前应安装的 APK

| 用途 | 文件 | ABI | SHA-256 |
|---|---|---|---|
| 康佳电视 / Android 4.4.4 | `outputs/PENG-Movie-API19-armeabi-v7a-release.apk` | `armeabi-v7a` | `e532f4e717d2ee3429e10bb8e502cf0ce8641972e1a071f5317665a869e66d98` |
| 64 位电视或手机 | `outputs/PENG-Movie-arm64-release.apk` | `arm64-v8a` | `b65f5c26dbc444e17d5ed03cd21a5b1c6bc1a6ffb2e60e2bc4faef23edd6f347` |

不要给这台康佳电视安装旧的 `PENG-Movie-API19-arm64-release.apk`。该文件只包含 `arm64-v8a` JNI，不能匹配电视的 32 位应用进程。

## 已验证的第三方影视应用

| 应用 | 版本 | 来源 | 兼容性与验证结果 |
|---|---|---|---|
| VLC for Android | 3.7.1 | [VideoLAN 官方下载](https://get.videolan.org/vlc-android/3.7.1/VLC-Android-3.7.1-armeabi-v7a.apk)，源码：[videolan/vlc-android](https://github.com/videolan/vlc-android) | 选择 `armeabi-v7a` APK；Manifest 为 `minSdkVersion=17`、`targetSdkVersion=36`。已于 2026-10-31 通过 ADB 在本机安装成功，`org.videolan.vlc/.StartActivity` 可恢复到前台且进程持续运行，未见崩溃。首次使用引导尚未完成，未验证实际媒体播放。 |
| TVBoxOS | 当前 `main`：`ab11d289e09963a9daf65ca7f6b7a9a8cbe184e1` | [q215613905/TVBoxOS](https://github.com/q215613905/TVBoxOS) | 已构建 `java32Debug` 产物 `TVBox_debug-java32.apk`（SHA-256：`f93795524940adc3cf853006b36faba5a88b5733d2c78eba3f308bea63c72409`）。最终 Manifest 为 `minSdkVersion=19`、`targetSdkVersion=28`，且只含 `armeabi-v7a` JNI 库。电视上已有同包名 `com.github.tvbox.osc.jun`、不同签名的版本，覆盖安装被 `INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES` 阻止；为保留原应用数据，未卸载。已安装的同包名版本可启动到 `HomeActivity`。 |
| 我的电视（MyTV） | 1.4.4 通用版 | [lizongying/my-tv](https://github.com/lizongying/my-tv)，[官方 Release APK](https://github.com/lizongying/my-tv/releases/download/v1.4.4/my-tv-v1.4.4.apk) | 该项目源码 `minSdk=17`，Release APK 的 Manifest 为 `minSdkVersion=17`、`targetSdkVersion=33`。已于 2026-10-31 在本机 ADB 安装成功，`com.lizongying.mytv/.MainActivity` 在前台、进程持续运行，未见启动崩溃。此版本为支持 Android 4.2+ 的通用直播版；未验证各直播频道的可用性。 |

VLC 适合播放 U 盘、本地硬盘、SMB/NAS、DLNA/UPnP 及用户合法拥有的网络媒体；支持字幕、多音轨及软硬解切换。下载包 SHA-256 已按 VideoLAN 同目录校验文件验证。

TVBoxOS 的上游 Room 2.3 注解处理器无法在 macOS ARM64 主机加载 SQLite 校验库；本次临时构建将 `room-runtime` 与 `room-compiler` 同步为 2.5.2 以完成编译。该调整不改变 `java32` 的 API 19 或 ABI 配置。

## 手机投屏能力

电视预装 `com.konka.kkmultiscreen`（康佳多屏互动），其启动 Activity 为 `com.konka.kkmultiscreen/.KKMutiScreenTvActivity`，已于 2026-10-31 通过 ADB 启动确认。该包还包含康佳本地媒体分享播放器和 `com.hpplay.sdk.sink` 接收端组件。

系统 `WifiDisplayStatus` 显示 `featureState=2`、`wfdType=1`，说明存在 Wi-Fi Display / Miracast 能力；但当前 `wifi_display_on=0`，即无线显示尚未启用。启用前应通过电视设置或“多屏互动”界面确认，不要通过 ADB 强行修改该开关，以免 Wi-Fi Direct 切换影响当前网络连接。

### 建议使用方式

1. 电视打开“多屏互动”，手机与电视连接到同一局域网。
2. Android 手机在控制中心或“设置 → 连接与共享”中打开“投屏 / 无线显示 / Smart View”，选择电视显示的设备名称；这适合完整镜像手机画面。
3. iPhone/iPad 可先在“多屏互动”页面确认是否显示 AirPlay 接收名称，再从控制中心的“屏幕镜像”选择该名称；`hpplay` 组件表明系统具备相关接收能力，但尚未在本机用 iOS 设备实测。
4. 只投送某个应用内的视频时，优先使用该手机应用的“投屏”按钮（DLNA）；相比完整屏幕镜像，延迟和耗电通常更低。

### 易互动手机端 APK 分析

电视二维码对应的下载包为 `http://tv.kkapp.com/iwonka/android/app/10001/MultiScreen.apk`。2026-10-31 下载的包 SHA-256 为 `f42ee594169b612df9c50ac082774842db02276b5a3f803a74061b4a079c3c64`，包名 `com.konka.MultiScreen`，版本 `7.7.94233`，`minSdkVersion=21`；它是手机端应用，不能安装到本机 API 19 电视。

该 APK 集成乐播/HPPlay SDK，不是通过 Android Miracast 发送画面：它使用 DLNA、JmDNS/mDNS 等局域网发现机制定位接收端，再经 `MediaProjection` 获取用户授权的屏幕画面，由 `ScreenCastService` 在前台服务中编码并通过 HPPlay 的镜像通道发送到电视。APK 内含 `libhpplayae`、实时传输和 FFmpeg/IJK 相关库。电视端日志中的 `com.hpplay.sdk.sink` 与这一发送端相匹配。

镜像服务在手机任务被移除时会主动停止，因此手机端不要划掉易互动任务；同时应允许其后台运行并关闭电池优化。该包请求联系人、位置、设备状态、录音、相机、存储和网络等较宽权限，且允许明文网络流量；只应从康佳提供的下载地址安装并按实际功能授权。

### 自建局域网投屏原型

为避免依赖 HPPlay 私有协议，项目新增一套独立的局域网投屏实现：

| 组件 | 模块 / 包名 | 系统要求 | 职责 |
|---|---|---|---|
| 电视接收端 | `cast-receiver` / `com.peng.cast.receiver` | Android 4.4.4（API 19）及以上 | TCP 接收 H.264 并用系统 `MediaCodec` 解码到电视画面 |
| 手机发送端 | `cast-sender` / `com.peng.cast.sender` | Android 5.0（API 21）及以上 | 经用户授权的 `MediaProjection` 捕获屏幕，硬编码 H.264 并发送 |
| 共用协议 | `cast-protocol` | Java | 定义封包、视频配置及帧格式 |

发送端在 Android 10（API 29）及以上且用户授予录音权限时，使用 `AudioPlaybackCapture` 捕获允许被采集的应用播放声音，编码为 AAC 后由电视的系统解码器播放。API 21–28 没有通用的内部音频采集能力，仍只传输画面；DRM、`FLAG_SECURE` 或明确禁止音频捕获的应用也不会输出声音。手机和电视必须处于同一可信局域网；协议当前未提供配对、加密、自动发现、断线重连、方向变化重协商或码率自适应。

电视端监听 TCP `48999`，包头 magic 为 `0x50434153`、协议版本为 `1`。发送端先发送包含宽高、SPS（`csd-0`）与 PPS（`csd-1`）的配置包，再发送带展示时间戳和 `MediaCodec` flags 的 H.264 访问单元。视频最长边限制为 1280、30 fps、3 Mbps、关键帧间隔 1 秒，以优先低延迟和 API 19 硬解兼容性。

2026-10-31 已在本机重新构建两个 debug APK，并将接收端安装到康佳电视。`com.peng.cast.receiver/.ReceiverActivity` 可正常前台运行，界面显示有线网络地址 `192.168.31.239:48999`；从局域网主机对该端口的 TCP 连接成功。接收端会枚举所有本机 IPv4 地址，因此有线联网时不会错误提示“未连接 Wi-Fi”。

手机端已在华为 `ALN-AL00`（OpenHarmony 7.0.0.105）通过卓易通安装并运行。HDC 的 `install` 只接受 HAP，不能直接导入 APK；可将 APK 传至手机“我的手机”根目录，再在系统文件管理中打开，由卓易通完成依赖检查和安装。发送端已获得系统屏幕录制授权，并成功将手机画面显示到康佳电视。

首次使用手机原始竖屏 `592x1280` H.264 流时，康佳 API 19 的 HiSilicon 解码器返回 `omx error 0x90000002`。发送端现强制使用 AVC Baseline、Level 3.1，并将竖屏源编码到标准 `1280x720` 画布；接收端将 SPS/PPS 合并为单一 `csd-0` 配置以兼容旧硬解。实机确认该配置可持续显示手机画面。当前发送端 debug APK 的 SHA-256 为 `7d393fdca2ecb85fa79c5afb7ca3a45266cba3f81cf30ba0148fdd9de33fff76`。

**后台与全屏验证（未通过）**：发送端投屏后按 Home 离开 `PENG投屏`，再启动横屏全屏的 `PENG电视`。手机可进入该应用的“正在加载...”全屏界面，但电视接收端记录 `SocketException: ECONNRESET`，说明发送端连接已经关闭。随后使用手机的“红果免费短剧”播放《全民沙漠求生：从一瓶水开始》第 1 集，手机端视频可正常持续播放；但因离开 `PENG投屏` 时会话已断开，电视无法接收该视频画面。当前卓易通环境下尚不能确认后台投屏持续可用，也未能验证真实视频播放或全屏视频画面；`PENG电视` 本次未完成内容加载。须先定位 MediaProjection/前台服务为何在切换应用后被卓易通停止，再重测普通视频和受 DRM 保护视频（后者可能因系统安全限制显示黑屏）。

发送端 `0.1.1`（SHA-256：`c493d6b0d061a5301d82468c334247ab2b14af1d0d3b756e27f5571a1d2c7e66`）已在华为手机的卓易通更新安装。此版本显式以 `mediaProjection` 类型提升前台服务、任务移除时不停止服务，并在投屏期间持有部分唤醒锁。更新后的后台保活仍待实测：本次重新发起授权时，红果遗留的悬浮播放层覆盖系统录屏授权界面，无法完成授权交互。

发送端 `0.1.2`（SHA-256：`23b662bccefae309f4f18f142e71ccc5290878c1aa4d944ccb401c13d0a85997`）修复 Android 12+ 创建通知 `PendingIntent` 必须指定可变性标志的崩溃。在本机 Android 12（API 32）虚拟设备上，授权投屏后电视接收端成功收到 H.264 配置与首帧；随后切换到系统设置页，发送端仍为前台服务、`MediaProjection` 仍处于 `TYPE_SCREEN_CAPTURE`，电视端 TCP 连接保持 `ESTABLISHED`。结论：在标准 Android 上，发送端可以在切换到其他应用后继续投屏。另一个 Android 7.0（API 24）虚拟设备因系统 `OMX.google.h264.encoder` 进程段错误而停止编码，此为该旧模拟器系统编码器缺陷，不能用于验证服务生命周期。

**视频播放器验证**：在同一 Android 12 虚拟设备安装开源 `mpv-android`（arm64 官方 Release），并使用本机生成的 20 秒非受保护 MP4 测试片段播放。mpv 日志确认首个视频帧已显示、GPU 视频渲染已启用，并以 `finished playback, success` 正常结束。播放过程中发送端仍为前台服务、`MediaProjection` 仍有效，康佳接收端 TCP 会话保持 `ESTABLISHED`，接收端未记录解码或连接错误。因此，标准 Android 上的普通本地视频播放可持续镜像到电视。此结论不适用于 DRM 或 `FLAG_SECURE` 内容；首版仍不传输系统音频。

## 已知问题、原因与处理方式

### 1. 首页启动时 API 19 资源崩溃

**现象**

```text
Resources$NotFoundException
UnsupportedOperationException: Can't convert to color: type=0x2
```

崩溃根资源为 `shape_toplayout_tvname.xml`，以及同类 drawable 中的 `?attr/color_theme*`。

**原因**

Android 4.4 的 `GradientDrawable` 不能把主题属性引用稳定地解析成颜色值；较高版本设备不会复现。

**已完成修复**

`app/src/main/res/drawable/` 下相关 XML 已将 `?attr/color_theme*`/`?color_theme*` 改为 `@color/color_theme*`。当前 drawable 目录已检查，无残留的上述主题颜色属性引用。

**状态：已修复。**

### 2. 内置影视配置和内置直播源提示读取失败

**现象**

点击“内置影视配置”后提示读取失败。

**日志证据**

旧 Release 有：

```text
BuiltInSource: Reading asset default-configs.json for configs
BuiltInSource: No valid sources in default-configs.json
```

但 APK 中实际包含配置文件，且 JSON 中每一项都有 `name`、`url`。

**原因**

Release 启用了 R8 混淆，而 `BuiltInSourceRepository.Source` 的 Gson 字段没有完整 `@SerializedName` 映射。混淆后 Gson 无法用 JSON 的 `name`、`url` 写入字段，全部条目被判为无效。

**已完成修复**

为 `id`、`name`、`url`、`description`、`group`、`groups`、`default` 添加显式 `@SerializedName`。

**修复验证**

电视日志已确认：

```text
Loaded 31 entries from default-configs.json
Displayed 31 built-in config sources
Loaded 33 entries from default-live-sources.json
Displayed 33 built-in live sources
```

**状态：已修复并已在电视验证。**

### 3. “加载成功”但首页没有任何视频

**现象**

首页显示配置/Jar 加载成功，但没有分类或视频列表。

**原因**

“加载成功”只表示远程配置或 Spider Jar 下载、解析完成，不表示 Spider 的 `homeContent()` 已成功返回数据。日志证明首页请求实际在创建 Spider 时失败，之前返回了空 `SpiderNull`，因此 UI 没有可展示的数据。

**关键日志**

```text
Loading home content source=点我切源, type=3, api=csp_DouDouGuard
Failed to create spider
ExceptionInInitializerError
Caused by: NullPointerException
at com.github.catvod.spider.DexNative.<clinit>
```

**状态：见下一节 Spider 兼容性；尚待最新 v7 包在电视验证。**

### 4. 远程 Spider Jar 无法在 API 19 运行

**实际 Jar 的结构**

当前远程配置引用的 Spider Jar 经下载与 MD5 校验后确认包含：

```text
classes.dex
assets/wexguard_v7.so
assets/wexguard_v8.so
assets/wexshinidie.guard
```

- `wexguard_v7.so` 是 32 位 ARM ELF；
- `wexguard_v8.so` 是 64 位 ARM ELF；
- 本电视应使用 `wexguard_v7.so`。

**旧故障链**

1. 最早调用 `Init.init(Context)` 后曾出现 native `SIGSEGV`。
2. 为避免整个进程退出，曾在 API 19 跳过 `Init.init(Context)`。
3. 反编译 Jar 后确认 `DexNative.<clinit>` 需要 `Init.context()` 和 `Init.classLoader()`；跳过初始化会令 `Init.context()` 为 `null`，必然产生 `NullPointerException`。
4. 反编译还确认 `BaseSpiderGuard` 调用宿主 `Spider.action(String)`，而旧宿主未定义该方法；电视日志有相应 Dalvik verifier 警告。

**已完成的兼容补丁**

- `com.github.catvod.crawler.Spider` 已补齐 `action(String)` ABI；
- `JarLoader` 已恢复 `Init.init(Context)`，让 Jar 正常解压/加载 v7 guard 并创建内部 Spider Dex loader；
- `JarLoader` 初始化线程发生 Java 异常时不再把 Jar 标为成功，而是记录异常并使加载失败；
- 电视包改为 `armeabi-v7a`，使 APK 自身的 JNI 与电视进程 ABI 一致。

**状态：待真机验证。**

必须在电视安装 `PENG-Movie-API19-armeabi-v7a-release.apk` 后检查：

1. 是否不再出现 `DexNative.<clinit>` 的空指针；
2. 是否没有新的 `Fatal signal 11 (SIGSEGV)`；
3. `JarLoader` 是否出现 Spider 初始化完成日志；
4. `SourceViewModel` 后是否出现首页分类和视频列表。

### 5. 直播播放的 IJK JNI 崩溃

**旧日志**

```text
java.lang.UnsatisfiedLinkError:
Native method not found:
tv.danmaku.ijk.media.player.IjkMediaPlayer.native_init:()V
```

**原因**

旧交付包只有 `arm64-v8a` IJK 库，电视应用进程却是 32 位，因此 native 方法没有实现可加载。

**已完成修复**

- 新增并构建 `armeabi-v7a` 电视包，确认其中包含 `libijkplayer.so`；
- `PlayerHelper` 不再因 API 19 无条件禁用 IJK，而是尝试装载 IJK JNI；
- 仅在发生 `UnsatisfiedLinkError` 时回退到 Android 系统播放器。

**状态：待真机验证 IJK 直播与点播。**

### 6. API 19 电视没有 IPv6 路由

**日志**

```text
connect failed: ENETUNREACH
bfzyapi.com/...:443
```

电视先连接到 IPv6 地址，而网络没有 IPv6 路由。

**已完成修复**

API 19 的 OkHttp DNS 解析现在优先返回 IPv4 地址；若 DoH 查询失败，则尝试系统 DNS 后再筛选 IPv4。

**状态：代码已修复，待真机网络请求验证。**

### 7. 手机与电视 UI 适配

**已完成**

- 用 `UiModeManager`、Leanback/Television feature 判断 TV，不再用屏幕尺寸或电话能力猜测；
- 电视保持横屏、沉浸式、遥控器焦点缩放；
- 手机浏览页面允许竖屏和横屏，保留系统栏；
- 手机竖屏首页/分类网格使用两列，横屏使用四列；
- 播放页面仍默认横屏；
- `BaseActivity` 在配置变化后重新应用 AutoSize，避免方向切换密度错误；
- `layout-port/` 提供手机首页和配置对话框布局。

**已验证**

API 24 手机模拟器与 API 31 TV 模拟器可以启动。手机模拟器不等同于电视的 API 19/32 位 native 验证环境。

### 8. 易互动镜像会话被系统无操作超时停止

**现象**

手机通过易互动镜像到电视后会自行断开。

**日志证据**

2026-10-31 的电视日志表明，`com.hpplay.sdk.sink.business.BusinessActivity` 的镜像会话结束前，系统先发出 `konka.action.NO_OPERATION_TIMEOUT`。随后接收端依次进入 `onStop()`、`stopBusiness`、`PlayController.stopCast()` 和 `onDestroy()`，并返回康佳多屏互动主界面。日志中没有先于该广播的 Wi-Fi Display 链路断开记录。

**原因**

康佳系统的独立“无操作超时/屏保”机制仍在运行；它不受 `screen_off_timeout=2147483647`（永不熄屏）控制。多屏互动接收 Activity 未持有阻止该机制的唤醒状态，因此会被系统停止。

**建议处理**

优先在电视设置中关闭或延长“屏保 / 无操作超时 / 自动待机”。如设置界面没有入口，可在用户确认后将系统 `screensaver_enabled` 设为 `0` 并重新测试；此修改会关闭系统屏保，应由用户决定是否启用。手机端还应将易互动加入电池优化白名单，并避免切换 Wi-Fi、VPN 或移动热点。

## 推荐安装与日志采集

如可通过 ADB 连接电视，安装电视包：

```bash
adb connect 192.168.31.239
adb install -r outputs/PENG-Movie-API19-armeabi-v7a-release.apk
```

安装后按以下步骤复现：

1. 打开 PENG影视，等待首页完成加载；
2. 切换一次影视源并返回首页；
3. 进入任意视频详情并播放；
4. 打开设置中的“内置影视配置”和“内置直播源”；
5. 导出 logcat。

重点过滤标签：

```text
JarLoader
SourceViewModel
ApiConfig
BuiltInSource
ApiDialog
PlayerHelper
AndroidRuntime
```

建议额外保留以下模式的完整上下文：

```text
Fatal signal
SIGSEGV
UnsatisfiedLinkError
DexNative
ExceptionInInitializerError
```

当前电视日志文件位置：

```text
outputs/crash.log
```

该文件会被新的导出覆盖；每次复现前建议按日期另存一份。

## 当前验收清单

- [x] API 19 drawable 颜色解析修复。
- [x] 内置影视配置读取修复并在电视日志验证。
- [x] 内置直播源读取修复并在电视日志验证。
- [x] 构建真正匹配电视进程的 `armeabi-v7a` APK。
- [x] Spider 宿主 ABI 补齐与初始化失败显式处理。
- [x] IJK 按 JNI 实际可用性选择，缺失时回退系统播放器。
- [ ] 在电视验证 Spider `Init.init()` 与 `DexNative`。
- [ ] 在电视确认首页分类与视频列表出现。
- [ ] 在电视确认 IJK 点播/直播播放。
- [ ] 在电视确认 IPv4 优先网络请求可成功完成。
