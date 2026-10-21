# 16 KB 原生库兼容性处理

2026-10-16。本批针对用户报告的 16 KB 提示进行排查；尚未完成整个应用的 16 KB 适配。

## 已完成

- 将项目内 ARM64 `libc++_shared.so` 更新为 Android NDK r28c
  （28.2.13676358）提供的运行库，仅使用同一工具链去除调试符号。
  来源、原文件及提交文件的 SHA-256 见
  [原生库来源记录](../app/src/main/jniLibs/README.md)。
- 根据最终 APK 检查 C++ 运行库的消费者，目前仅 `libcaesarson.so`
  动态依赖它。原运行库提供且被消费者导入的 18 个符号，新运行库均保留。
  此项为符号兼容性检查，不能替代登录功能真机回归。
- Release 构建成功；APK 签名验证及 `zipalign -c -P 16 4` 通过。
  APK SHA-256：`88c45745cf9cc90e0acbc4d145dd45957ab560d07d0768fb46341ba0d33d6920`。
- 校验最终 APK 只有 C++ 运行库发生原生文件变化，其余 17 个库保持原哈希；
  Caesarson 的 JNI 返回对象字段保留，assets 中没有可执行 DEX。
- 增加 [最终产物检查脚本](../scripts/verify_native_page_size.py)，在 CI 中检查
  Debug 与 Release 包。检查失败阻断发布，不新增对齐问题豁免。

## 仍未解决

最终 APK 的 18 个 ARM64 库中，15 个通过静态检查，以下 3 个未通过：

| 库 | 发现 | 当前处理 |
| --- | --- | --- |
| `libcaesarson.so` | LOAD 只有 4 KB 对齐；文件偏移与虚拟地址在 16 KB 下不同余；RELRO 向页边界扩展会覆盖可写数据 | 缺少源码及兼容供应商构建，未修改 |
| `libnetdev-1.0.0.so` | LOAD 对齐值足够，但 RELRO 向 16 KB 页边界扩展会覆盖可写数据 | 排查中发现，缺少兼容供应商构建，未修改 |
| `libnetmobsec-4.4.7.so` | LOAD 对齐值足够，但 RELRO 向 16 KB 页边界扩展会覆盖可写数据 | 排查中发现，缺少兼容供应商构建，未修改 |

这几项均来自项目直接内置的网易登录相关运行库。修改 ELF 的对齐字段、
关闭系统提示或取消 RELRO，都不能作为本批的正确修复。

官方 Android 下载接口重定向到 9.6.08 的安装包，CDN 返回 HTTP 403。
随后用户提供了本地官方 ARM64/ARM32 包，现已完成核验：

- 路径：`D:\Download\NeteaseCloudMusic_Music_official_9.6.08.261015102648_3264.apk`。
- APK SHA-256：`4c2e8791c6d00b952332eb698837bbea72bb4384518b75fe895bd4fdccb572d3`。
- 包名 `com.netease.cloudmusic`，版本 9.6.08 / 9006008；APK 签名验证通过，
  签名证书 SHA-256 为 `54254d2be09daef48dedc2b4a4f497d153e14ed9d70814fc9c360ee9240827f7`。
- 包内 ARM64 的 `libcaesarson.so`、`libnetdev-1.0.0.so`、
  `libnetmobsec-4.4.7.so` 与项目现有文件字节完全一致，故仍未通过静态检查。
- 包内 `libc++_shared.so` 仍是原来的 4 KB 版本，不替代本批已更新的 NDK r28c 版本。
- 证据：被忽略的 `temp/official-9.6.08-native-report.json`。

因此，已有新版官方 APK，但其中没有可用的兼容替换库。仍需供应商提供适配
16 KB 且 JNI/协议兼容的构建或源码；取得候选库后再验证接口、动态依赖和登录流程。

手机在本批开始时已断开，尚未安装本批 APK，也未运行 16 KB 设备/模拟器测试。
既有完整 lint 的 102 条发现仍未清零；本批的 Release 构建成功不代表发布验收通过。

## 检查方法与证据

```text
python scripts/verify_native_page_size.py app/build/outputs/apk/release/app-release.apk --json-output app/build/reports/native-page-size-release.json
```

当前预期退出码为 1，准确报告上述三个未解决的库。APK 内压缩存储的原生库
按安装时提取处理；未压缩原生库另外检查 ZIP 数据起始位置是否按 16 KB 对齐。

RELRO 检查根据 Android Bionic 链接器的页边界保护范围，检查是否覆盖 RELRO 外
的可写 LOAD 字节。并非仅凭 RELRO 结束地址不是 16 KB 的整数倍就判失败：
新版 LLD 会在下一段可写数据之前保留间隔，该布局可以通过此检查。

- [Android 官方适配指南](https://developer.android.com/guide/practices/page-sizes?hl=zh-cn)
- [Bionic RELRO 保护实现](https://android.googlesource.com/platform/bionic/+/master/linker/linker_phdr.cpp)
- 被忽略的本地证据：`temp/native-page-size-before.json`、
  `temp/cpp-runtime-abi-check.json`、`temp/native16k-auth-expected.json`、
  `temp/release-install-build.log`，以及最终 `app/build/reports/native-page-size-release.json`。
