# APK 反编译归档

本目录保留从 `当贝桌面_4.1.7_final_AC_修改版(1).apk` 反编译得到的材料，供逐页迁移到 `launcher/`。

- `apk-decompile/sources/`：JADX 输出的 3,758 个 Java 文件。
- `apk-decompile/resources/`：解码后的 AndroidManifest、`res/`、`assets/` 及签名元数据。

这些文件**不属于 Gradle 的编译源集**。原 APK 使用旧版 Support Library、Dagger、RxJava、ButterKnife 和多个当贝私有库，且部分符号已混淆；直接纳入 `launcher/` 不会得到可维护或可构建的工程。迁移时应先替换私有依赖与系统权限调用，再将一个页面及其资源移入 `launcher/`。

页面、跳转与优先级见仓库根目录的 [`RECOVERY.md`](../RECOVERY.md)。
