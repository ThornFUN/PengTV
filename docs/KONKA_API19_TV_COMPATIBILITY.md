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
