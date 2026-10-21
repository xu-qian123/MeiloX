# 1.55.0 切歌崩溃修复

2026-10-16，合并后的 Release 安装到手机后，用户报告切换下一首闪退。启动成功不能替代播放和切歌验收。

## 两处独立原因

1. 10:45 的崩溃为 `UnsatisfiedLinkError`：播放记录 NCBL 编码触发 Zstd 初始化，而应用依赖默认桌面 JAR，APK 的 `lib/arm64-v8a/` 缺少对应 JNI 库。改用同版本 `@aar`，JVM 测试继续使用桌面 JAR。官方 Android 用法见 [zstd-jni 文档](https://github.com/luben/zstd-jni#android-support)。
2. 修复压缩库后，10:52 的实际切歌复测出现 `NoSuchMethodError`：`lyrics-ui:1.0.19` 的预编译代码调用 `KaraokeSyllable.copy$default` 的四字段版本，而合并后的 `lyrics-core:0.5.0` 改为六字段版本。该 UI 的已发布 Android POM 明确依赖 `lyrics-core-jvm:0.4.5`。将核心库严格锁定为 0.4.5，与保留的 UI 配套；以后必须成对验证升级。

## 修改与回归

- NCBL 压缩边界仅将 `LinkageError` 转为 `IOException`，交由既有上报失败处理；不吞掉虚拟机致命错误，也不关闭播放记录上报。
- JVM 回归覆盖原生链接失败、虚拟机错误透传，以及歌词 UI 实际调用的预编译方法签名。
- Android 回归实际执行 Zstd 压缩/解压、连续 20 次 NCBL 编码，以及窄布局下中英文逐字歌词切换渲染。
- 最终主代码构建成功：`testDebugUnitTest`、Debug / AndroidTest / Release APK，103 个 suite、661 个单元测试，0 失败/错误/跳过。构建日志：被忽略的 `temp/ncbl-crash-fix-build.log`。
- 三项真机专项测试全部通过：Android 压缩/解压、20 次 NCBL 编码、逐字歌词切换渲染。证据：被忽略的 `temp/track-crash-device-tests-final.log`。最初的 Espresso 方案受 Android 17 InputManager API 影响；ActivityScenario 应用内启动被设备拦截，改为系统启动宿主及平台布局同步后通过，未绕过产品逻辑。
- 最终 Release 签名校验通过，包含 ELF 文件 `lib/arm64-v8a/libzstd-jni-1.5.7-23.so`；APK SHA-256 为 `e7aa9b7da3849ebbad9d26eeaa73beeebccdeda85da8936ef438e38bf77ed345`，手机安装文件哈希一致。版本 1.55.0 / 14，沿用本机调试密钥签名。
- 覆盖安装保留原有数据，并移除临时测试包。Release 实际播放从 The Middle 切到 Post Malone 后保持 PLAYING，同一进程 20303，崩溃日志没有新增记录。

既有完整 lint 的 102 条发现仍待处理。本修复不扩大基线；Release 的 `lintVital` 不能替代完整 lint。
