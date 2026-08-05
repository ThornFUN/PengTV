# PENG 桌面

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
git clone https://gitee.com/ThornFUN/peng-launcher.git
cd peng-launcher
./gradlew assembleDebug
```

生成的调试 APK 位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 构建签名 Release APK

项目使用根目录的 `def_sign.jks` 作为调试用途的 Release 签名证书。密码不写入仓库；构建时通过 Gradle 属性传入：

```bash
./gradlew assembleRelease \
  -PPENG_STORE_PASSWORD="<keystore-password>" \
  -PPENG_KEY_PASSWORD="<key-password>"
```

签名后的 Release APK 输出到：

```text
app/build/outputs/apk/release/PENG-Launcher-v<version>-release.apk
```

如已连接设备或模拟器，可直接安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次使用时，在系统设置中将 **PENG 桌面** 设为默认桌面应用，或从应用列表直接启动。

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
app/
  src/main/
    java/com/penglauncher/recovered/
      MainActivity.java        # 首页、全部应用页、卡片管理和遥控器导航
      FeatureActivity.java     # 设置、壁纸选择和关于页
      BasePageActivity.java    # 设置页卡片与 TV 焦点样式基类
    res/
      drawable-nodpi/          # 首页背景和 PENG Logo
      values/                  # 应用名称与主题
RECOVERY.md                    # 原 APK 结构和恢复记录
TV_UI_SPEC.md                  # TV 焦点与设置页面 UI 强制规范
```

## 定制

| 内容 | 位置 |
|---|---|
| 应用名称 | `app/src/main/res/values/strings.xml` |
| 应用 Logo | `app/src/main/res/drawable-nodpi/ic_peng_logo.png` |
| 默认首页背景 | `app/src/main/res/drawable-nodpi/bg_launcher_default_new.webp` |
| 关于页文案 | `FeatureActivity.java` 的 `about()` |
| 首页卡片数量 | `MainActivity.java` 的 `DESKTOP_CARD_COUNT` |

如果修改 TV 交互控件，请严格遵循 [`TV_UI_SPEC.md`](TV_UI_SPEC.md)：所有可交互元素必须提供清晰可辨的默认态、焦点态和按压态。

## 原 APK 还原材料

仓库保留了 [`RECOVERY.md`](RECOVERY.md) 和 `recovered/README.md`，说明原当贝桌面 APK 的反编译材料、页面关系和还原过程。

完整反编译归档不纳入版本控制：其中包含原 APK 的签名、服务配置及第三方材料，不属于本项目的可维护运行源码。

## 贡献

欢迎提交 Issue 和 Pull Request。提交前请确保：

1. 使用 `./gradlew assembleDebug` 构建通过。
2. 新增遥控器交互满足 [`TV_UI_SPEC.md`](TV_UI_SPEC.md)。
3. 不提交构建目录、`local.properties`、密钥、原 APK 或其他敏感材料。

## 许可证

本项目采用 [MIT License](LICENSE)。
