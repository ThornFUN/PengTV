# PENG Android TV 套件

[English](README.md) | 简体中文

本工程包含三个可独立安装的应用：PENG 桌面、PENG直播 与 PENG影视。已构建的 release APK 会集中复制到工程根目录的 [`outputs/`](outputs/)。

## 已构建 APK

| 应用 | APK | 兼容范围 | 签名 | SHA-256 |
|---|---|---|---|---|
| PENG 桌面 | `outputs/PENG-Launcher-v0.1.0-release.apk` | Android 4.0 / API 14 及以上；不含 native 库，支持设备自身 ABI | 已使用本地 release 签名 | `6a7f45576b2e8f6123879f9b2ec29750f1c4ac9a3d956f6f59a46a660d644d0f` |
| PENG直播 | `outputs/PENG-Live-v0.1.0-release.apk` | Android 4.4 / API 19 及以上；不含 native 库，支持设备自身 ABI | 新版本需重新构建 | — |

## 应用概览

| 模块 | 包名 | 定位 | 视频源策略 |
|---|---|---|---|
| `launcher/` | `com.peng.launcher` | 默认桌面与应用管理 | 不提供视频播放或视频源 |
| `live-show/` | `com.peng.live.show` | 轻量 IPTV 直播播放器 | 本地默认公开频道；用户可添加拥有使用权的 M3U 与 XMLTV 地址 |
| `movie/` | `com.peng.movie` | 基于 TVBox 的影视与直播客户端 | 影视与直播源由用户配置 |

## PENG 桌面

PENG 桌面是一个面向 Android TV、电视盒子和大屏设备的轻量级开源启动器。它以遥控器方向键为核心交互方式，提供可自定义的首页应用卡片、独立的全部应用页、系统 Wi-Fi 入口、本地壁纸和应用管理功能。

> 最低支持 Android 4.0（API 14）；当前 `targetSdk` 为 Android 15（API 35）。

## 功能

- 固定两行、共 10 个首页应用卡片位。
- 点击卡片启动应用；按遥控器菜单键替换或移除首页卡片。
- 首页第二行按下键进入“全部应用”页；全部应用页首行按上键返回首页，两个页面使用垂直滑动动画切换。
- 全部应用页展示所有可启动应用；按菜单键可打开系统应用详情或卸载应用。
- 支持从本地文件选择图片作为首页壁纸，图片会复制到应用内部存储。
- 设置页提供系统 Wi-Fi 设置入口和关于页面。
- 所有可交互控件具备明确的默认、焦点和按压状态，适合遥控器操作。

## 环境要求

| 项目 | 要求 |
|---|---|
| Android Studio | 支持 AGP 8.6.1 的版本 |
| JDK | 17 |
| Android SDK | API 35（用于编译） |
| Gradle | 项目内置 Wrapper，首次构建会自动使用 Gradle 8.11.1 |
| 运行设备 | Android 4.0 / API 14 及以上，横屏 TV 或盒子设备 |

## 快速开始

```bash
git clone https://github.com/ThornFUN/PengTV.git
cd peng-launcher
./gradlew assembleDebug
```

生成的调试 APK 位于：

```text
launcher/build/outputs/apk/debug/launcher-debug.apk
```

### 构建签名 Release APK

Release 签名信息不纳入版本控制。复制 `keystore.properties.example` 为
`keystore.properties` 并填入本地证书路径和口令后构建：

```bash
cp keystore.properties.example keystore.properties
./gradlew assembleRelease
```

签名后的 Release APK 输出到：

```text
launcher/build/outputs/apk/release/PENG-Launcher-v<version>-release.apk
```

如已连接设备或模拟器，可直接安装：

```bash
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
```

首次使用时，在系统设置中将 **PENG 桌面** 设为默认桌面应用，或从应用列表直接启动。

## PENG直播

`live-show/` 是与 PENG 桌面同仓库、但可独立安装的 IPTV 应用，包名为
`com.peng.live.show`。它不声明 HOME，不会改变默认桌面。

- 内置 `assets/default_channels.tsv` 中的默认频道；可追加用户有权使用的 M3U 频道源。
- 可选导入 XMLTV 地址，在频道列表中显示节目单预览。
- 可在电视端填写地址，或在“手机导入”页使用同一局域网手机访问电视
  `8081` 端口填写地址；该服务只在导入页面可见时运行。
- 采用兼容 Android 4.4 的 ExoPlayer HLS 播放器，适合设备自身支持的 HTTP/HLS 流。
- 默认采用稳定优先的 20 至 30 秒缓冲策略，可选流畅（480P）、均衡（720P）和高清（1080P）三档；线路缓冲超时或播放失败时优先切换备用线路，并记录线路启动速度和失败次数以优化后续排序。
- 频道按三位编号显示；应用启动时自动恢复上次选择的频道，播放页可按上、下键切换上一或下一频道，也可用数字键输入编号后按确认跳转。
- 播放页按菜单键打开频道列表，频道列表按返回键回到播放页；播放页连续按两次返回键退出 PENG直播。

单独构建 PENG直播：

```bash
./gradlew :live-show:assembleRelease
```

签名后的 APK 输出到：

```text
live-show/build/outputs/apk/release/PENG-Live-v<version>-release.apk
```

## PENG影视

`movie/` 是收编到 PengLive 根工程的 TVBox 影视客户端，包名为
`com.peng.movie`。它依赖 `movie/quickjs/`，Python 风味额外依赖
`movie/pyramid/`；默认的 Java 风味兼容 Android 4.4 / API 19。

构建 64 位通用 Debug APK：

```bash
./gradlew :movie:assembleArm64GenericNormalDebug
```

APK 位于：

```text
movie/build/outputs/apk/arm64GenericNormal/debug/PENG-Movie_debug-arm64-generic-java.apk
```

## 遥控器操作

| 场景 | 按键 | 行为 |
|---|---|---|
| 首页 | 方向键 | 移动首页卡片焦点 |
| 首页卡片 | 确定 | 启动应用 |
| 首页卡片 | 菜单键 | 替换或移除卡片 |
| 首页第二行 | 下键 | 切换到全部应用页 |
| 全部应用页 | 方向键 | 浏览应用 |
| 全部应用卡片 | 确定 | 启动应用 |
| 全部应用卡片 | 菜单键 | 打开应用详情或卸载 |
| 全部应用页首行 | 上键 | 切换回首页 |
| 任意设置页 | 返回键 | 返回上一页 |

在 Android 模拟器中，可使用键盘方向键模拟遥控器。也可通过 ADB 发送按键：

```bash
adb shell input keyevent 19  # 上
adb shell input keyevent 20  # 下
adb shell input keyevent 21  # 左
adb shell input keyevent 22  # 右
adb shell input keyevent 23  # 确定
adb shell input keyevent 82  # 菜单
adb shell input keyevent 4   # 返回
```

## 使用说明

### 自定义首页

首页最多有 10 个卡片位。初次启动会自动填充部分已安装应用。

1. 选中已有应用卡片并按菜单键。
2. 选择“替换应用”或“移除卡片”。
3. 选中空的“添加应用”卡片可添加新的应用入口。

首页布局会自动持久化；如果某个已固定应用被卸载，对应位置会变为空卡片。

### 设置本地壁纸

1. 在首页选择“设置”。
2. 打开“首页壁纸”。
3. 从系统文件选择器选择一张图片。

所选图片会复制到应用内部目录，因此原始文件被移动、删除或 U 盘拔出后，首页壁纸仍可正常使用。

### 应用管理

在全部应用页选中任意应用并按菜单键：

- **应用详情**：进入 Android 系统应用详情页。
- **卸载**：打开 Android 系统卸载确认页。

系统预装应用可能不支持卸载；这是 Android 系统限制。

## 项目结构

```text
launcher/
  src/main/
    java/com/penglauncher/recovered/
      MainActivity.java        # 首页、全部应用页、卡片管理和遥控器导航
      FeatureActivity.java     # 设置、壁纸选择和关于页
      BasePageActivity.java    # 设置页卡片与 TV 焦点样式基类
    res/
      drawable-nodpi/          # 首页背景和 PENG Logo
      values/                  # 应用名称与主题
live-show/
  src/main/
    java/com/peng/video/       # IPTV 播放、源配置和手机导入服务
    res/                       # PENG直播主题和 TV Banner
movie/
  src/main/                    # PENG影视应用源码与资源
  quickjs/                     # JavaScript 引擎库
  pyramid/                     # Python 风味库
outputs/                       # 已签名 APK 集中输出目录
config.gradle                  # 所有子应用的包名、版本和 SDK 配置
docs/                          # 项目设计、兼容性与恢复文档
```

## 定制

| 内容 | 位置 |
|---|---|
| 应用名称 | `launcher/src/main/res/values/strings.xml` |
| 应用 Logo | `launcher/src/main/res/drawable-nodpi/ic_peng_logo.png` |
| 默认首页背景 | `launcher/src/main/res/drawable-nodpi/bg_launcher_default_new.webp` |
| 关于页文案 | `FeatureActivity.java` 的 `about()` |
| 首页卡片数量 | `MainActivity.java` 的 `DESKTOP_CARD_COUNT` |

如果修改 TV 交互控件，请严格遵循 [`docs/TV_UI_SPEC.md`](docs/TV_UI_SPEC.md)：所有可交互元素必须提供清晰可辨的默认态、焦点态和按压态。

## 原 APK 还原材料

仓库保留了 [`docs/RECOVERY.md`](docs/RECOVERY.md) 和 `recovered/README.md`，说明原当贝桌面 APK 的反编译材料、页面关系和还原过程。

完整反编译归档不纳入版本控制：其中包含原 APK 的签名、服务配置及第三方材料，不属于本项目的可维护运行源码。

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请确保：

1. 使用 `./gradlew assembleDebug` 构建通过。
2. 新增遥控器交互满足 [`docs/TV_UI_SPEC.md`](docs/TV_UI_SPEC.md)。
3. 不提交构建目录、`local.properties`、密钥、原 APK 或其他敏感材料。

## 许可证

本项目采用 [MIT License](LICENSE)。
