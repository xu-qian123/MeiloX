# 优化执行记录

## 2026-10-15：批次 A/B，验证基线与 CI

当前范围：工具链确认、现有测试/lint/调试构建、CI 验证与报告。业务行为与数据格式尚未修改。

### 工具链

- 已运行仓库 Wrapper：Gradle 9.6.0。
- 已定位并实际使用 Eclipse Adoptium JDK 21.0.7：`D:\Java\gradle-jar\jdks\eclipse_adoptium-21-amd64-windows.2`。
- Android SDK：`D:\Java\AndroidSdk`。
- 只在验证进程内指定 JAVA_HOME 与 ANDROID_HOME，没有修改系统环境变量。
- 项目主代码和测试编译任务本次部分复用已有增量产物；这不是 clean 构建证明。

本次验证入口：

```powershell
$env:JAVA_HOME = 'D:\Java\gradle-jar\jdks\eclipse_adoptium-21-amd64-windows.2'
$env:ANDROID_HOME = 'D:\Java\AndroidSdk'
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --continue --console=plain
```

### 验证状态

| 项目 | 结果 |
| --- | --- |
| 单元测试 | 本次重新执行，63 个 suite、381 个测试，0 失败、0 错误、0 跳过 |
| lintDebug（改动前） | 失败：14 个已有错误、180 个已有警告；完整组合命令耗时 16m50s |
| lintDebug（门禁配置后） | 通过：无新增问题，198 项已审查存量发现由精确基线匹配；warningsAsErrors 已启用 |
| assembleDebug | 通过，本次重新打包 |
| assembleDebugAndroidTest | 通过，本次编译并打包；出现既有 UnstableApi opt-in marker 编译警告 |
| 真机/仪器化运行 | ADB 未发现连接设备，未执行 |
| 性能、耗电与音频试听 | 未执行，不以单元测试耗时代替真机基线 |
| GitHub Actions | 仅本地修改，未推送或触发远端运行 |

单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`；XML：`app/build/test-results/testDebugUnitTest/`。本机原始输出保存在被 Git 忽略的 `optimization-baseline.log`。

### CI 改动

- PR、push 和手动运行先进行无发布凭据的单元测试、lint、调试 APK 和仪器化测试 APK 构建；构建测试 APK 不代表运行了真机测试。
- 使用仓库 Wrapper，移除动态 Gradle current 版本选择。
- 报告在验证成功或失败后均尝试上传；未生成报告时给出提示。
- PR 仅验证；原有 APK 签名与发布链路保留，构建新增 verify 前置依赖。
- YAML 解析与 job 依赖关系已在本地检查；远端 runner、依赖下载和签名流程尚未实测。
- 启用 warningsAsErrors，新增错误和警告都阻断验证。`app/lint-baseline.xml` 保存首次报告的 194 项发现及复跑时人工审查确认的 4 条依赖元数据提示，共 198 项，使用相对路径和具体消息/代码位置，不关闭任何整类 lint 检查。
- 首次配置后检查成功匹配 194 项，并实际阻断另外 4 条依赖提示，验证警告确会导致失败。确认 `gradle/libs.versions.toml` 无修改后，仅补录 Hilt Navigation 与 Room 三个组件的四条提示；没有升级依赖。再次运行 lintDebug 通过。
- 依赖更新检查受远端元数据与缓存影响，未来可能在代码未变时提示新版本；须逐条审查，不自动刷新基线绕过检查。

### 未完成项与下一步

- 本地质量检查已完成；远端 Actions 仍需在推送后验证，本次未推送。
- 真机连接后完成阶段 0 性能基线及回归；在此之前可以推进独立的取消传播测试与修复。
- 现有问题若影响播放正确性或用户数据，优先修复并阻断发布；非关键问题登记具体处理批次和期限。
- 回退本批 CI 改动只需还原工作流文件，不涉及用户数据迁移。

### 既有 lint 问题登记与处理期限

基线是存量清单，不表示问题已解决。新增发现必须分析和修复；删除已修复条目，禁止重新生成基线来吞掉新增问题。下列期限按实施批次控制，同时最迟于 2026-10-29 复核处理进度；届时未完成项须重新说明原因与安排。

| 既有发现 | 数量 | 已核查事实与影响 | 处理边界/期限 |
| --- | --- | --- | --- |
| MissingPermission | 1 | 录音入口已有权限检查，构造 AudioRecord 在 runCatching 内；lint 未跨私有方法识别前置检查，拒绝/撤回权限仍需设备回归 | 批次 C 后紧邻处理，使权限契约可被静态检查识别；真机前完成权限回归 |
| NonObservableLocale | 2 | EQ 数值和日志日期在 Compose 中读取 Locale.getDefault，语言变化时可不刷新 | 批次 G 前修复 |
| LocalContextGetResourceValueCall | 10 | 页面/弹层通过 Context 读取资源，需逐处确认组合期与回调期，避免错误替换 | 批次 G 前完成分类与修复 |
| RestrictedApi | 1 | 系统歌词桥接有意调用 Media3 内部会话 API，已有源码注释，升级时有兼容风险 | 批次 H 前明确桥接契约、回归覆盖与最小范围处理；不全局禁用规则 |
| TrustAllX509TrustManager / CustomX509TrustManager | 3 | 不校验证书的客户端配置仅由 BuildConfig.DEBUG 分支调用；本次不修改现有网络行为，不据此声明网络已审计安全 | 批次 F 前复核调试用途与发布隔离；若证实生产可达则立即阻断发布并处理 |
| StaticFieldLeak | 2 | 已核查 CrashHandler.init 保存的是 context.applicationContext；不直接持有传入 Activity，仍需明确类型与静态检查约定 | 批次 G 前处理静态检查表达 |
| GradleDependency / NewerVersionAvailable | 43 | 依赖更新提示，不代表应立即升级；包含首次 39 条和复跑人工审查的 4 条元数据提示，自动升级会扩大本轮范围 | 批次 K 前评估兼容性，重要更新另列任务 |
| 其他布局、画中画、资源、日志及编码习惯 | 136 | 完整位置保存在基线；涉及界面行为的部分先复核再改 | 批次 G/J 治理，批次 K 前完成存量复核 |

任何发现一旦被确认会破坏播放正确性、用户数据或发布安全，不再按存量延期，立即升级为发布阻断项。

### 批次 C 的静态核查范围（实施前记录）

- safeApiCall 捕获取消异常，需显式重抛并验证成功、普通错误和取消场景；部分现有调用外层仍有 withContext 的取消保护，不据此推断每条调用都发生了旧状态提交。
- LyricManager 三处挂起读取设置的宽泛异常捕获会把取消转成默认配置；同步枚举解析与歌词解码的 catch 不机械修改。
- QQ 超时包裹的 fetchQQLyric 内部在独立作用域 launch，请求未完整纳入该超时的子任务；需在实际请求层建立超时边界，区分来源超时与外层取消，并保留原始加载会话标识。
- 已有 NetEase/AMLL/QQ/LRC 的取消重抛和加载会话校验应保留；后续为真实请求链补测试，不以单独的 token 类测试替代端到端状态验证。
- 手动 QQ 搜索和自动搜索结果存在代际归属待治理点，实施前先构造 A 慢 B 快及同曲重载场景确认。

以上是批次 A/B 时的候选修复点；后续实际修改与验证见本文件的批次 C 记录。

下一批回归用例：

| 用例 | 可控触发方式 | 必须保持的不变量 |
| --- | --- | --- |
| API 取消 | 在挂起请求完成前取消父任务 | 不返回 Resource.Error，不执行后续成功或失败提交 |
| 来源自身超时 | 使用虚拟时间令 QQ 实际请求超过局部 deadline | QQ 子请求结束；仅当前加载会话提交超时结果；其他来源可继续 |
| 外层取消 | 在局部超时结束前取消整个歌词加载 | 不发布来源超时提示，不继续搜索或映射写入 |
| A 慢 B 快 | 分别控制两次搜索/抓取的返回顺序 | A 不能覆盖 B 的结果或启动带 B 会话标识的 A 请求 |
| 同曲重载/自定义保存 | 旧请求挂起时开始同曲新会话或保存自定义歌词 | 新歌词、偏移和来源归属保持正确 |

这些测试应挂接实际加载协调代码；若为可测试性提取接口或纯协调器，生产调用必须真正使用它，避免只验证一套测试专用逻辑。

### 真机基线采集协议（待设备就绪执行）

使用同一 arm64 设备、系统版本、构建类型、设置与曲目集合；记录是否启用 AutoMix、EQ、响度均衡及歌词共享。性能比较使用可分析且接近发布优化的构建，调试构建的诊断结果不直接作为发布性能结论。

| 场景 | 记录内容 | 比较边界 |
| --- | --- | --- |
| 冷/热启动 | 首屏可交互时间、帧耗时 | 冷热样本分组，固定启动入口 |
| 点击播放/切歌 | 点击至 READY 与实际播放状态的时间，错误/重复跳转数 | 本地、完整缓存、网络音源分组；READY 不等同于实际出声 |
| 歌词 | 当前歌曲首份可用歌词到达时间、最终来源、串歌次数 | 冷缓存/热缓存分组；来源变化单独记录 |
| 播放器交互 | 展开/收起、进度拖动、歌词滚动期间帧耗时 | 同样操作脚本与时长，记录超帧预算比例与 P95 |
| 后台/AutoMix | 固定时段 CPU 时间、内存、异常与状态连续性 | 单路与交叠分组；耗电需要更长时间及一致温度/供电条件 |

交互与延迟场景先预热 3 次，每组采集至少 10 个有效样本，报告中位数、P95、原始样本和无效样本原因；样本不足时不对尾延迟作结论。后台观测使用固定时长重复运行，记录系统干扰。初始样本波动过大时先调整方法，不用调高阈值隐藏波动。禁止记录认证 Cookie 或完整签名音源 URL。

## 2026-10-15：批次 C，歌词取消、超时与结果归属

### 已实施

- safeApiCall 显式重抛 CancellationException，并在调用前、返回后及普通错误转换前检查取消状态；保留活跃请求成功与普通错误的原有返回形式。
- QQ 主歌词函数改为挂起调用，自动搜索、自动匹配时的映射保存、实际歌词请求与 LRC 兜底均在同一来源超时范围内等待。自动 QQ 任务是当前加载的子任务；手动选择的任务单独持有且可取消。
- 生产加载链使用 runLyricRequest 区分来源自身超时与外层取消；只有仍属于当前会话的来源超时可以更新状态。LRC 兜底超时不丢弃已经获取的主歌词。
- 已有 QQ 映射时，选歌列表查询与歌词请求并行；选歌列表查询不受主歌词的来源超时取消，但会随整个加载取消。没有映射时，为选择歌词来源而做的自动匹配仍在主歌词超时范围内。最终复核时修正了曾串行等待列表查询的写法，避免列表多等一个超时周期。
- 三项挂起设置读取通过 readLyricSetting 保留普通异常默认值，同时传播取消。预加载器的四处网络异常处理同样重抛取消。
- QQ 搜索结果由实际接入的 LyricSearchController 管理：新手动搜索取消旧搜索，加载/取消会使旧请求标识失效；自动搜索持有加载开始时的搜索标识，不能在手动搜索之后取得新标识再覆盖结果。
- 自动匹配移除共享 trySearchLastResult，搜索结果保留为局部变量；映射保存返回后再次检查原始加载标识，再开始歌词请求。本地/Room 歌词读取也按加载标识检查，覆盖同曲重载。

### 用户可见的修正

- 取消请求不再作为普通网络错误继续交给结果消费者。
- QQ 超时设置实际约束歌词请求，而非仅约束启动请求的函数。
- 手动选择 QQ 来源时立即使旧加载失效，避免等待映射保存期间旧来源仍继续提交；映射保存失败显示来源选择失败，不继续用旧请求更新界面。
- 较慢的旧搜索不再覆盖新搜索结果；来源切换和自定义歌词会话的归属检查保持有效。

没有修改数据库版本、偏好键、音频处理、播放队列或页面布局。

### 验证

- 先添加 ResponseHandlerTest，原实现 4 个用例中有 2 个失败，复现直接取消被转换为 Error、取消后结果消费者继续执行。
- 最终稳定源码的单元测试：66 个 suite、405 个测试全部通过，0 失败、0 错误、0 跳过；较原基线新增 24 个用例，包含底层忽略取消后迟到成功/网络失败的边界。
- 新用例覆盖：来源 deadline 取消实际挂起请求、外层/嵌套超时不误报来源错误、同曲重载和自定义会话使旧超时失效、挂起配置读取取消不回退继续执行、旧搜索忽略取消仍不能覆盖新结果、自动搜索不能覆盖手动结果。
- 三个并行查询用例已通过，覆盖选歌列表不等待歌词 deadline、歌词 deadline 不取消独立列表查询，以及取消整个加载会同时停止两者。
- 这些是 JVM 回归测试，验证已接入生产的请求边界与搜索协调器；不是完整 Android LyricManager、Room 和真实网络的设备集成测试。
- 最终组合验证（测试、lint、调试 APK、仪器化测试 APK）全部通过，耗时 390.28 秒，Gradle 退出码为 0。运行前后源码输入哈希一致；lint 无新增问题，仍为原有 198 条基线，没有扩大基线。执行命令为 `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process --continue --console=plain`，使用 JDK 21。日志与机器可读结果分别保存在被忽略的 `optimization-cancellation-final.log`、`temp/optimization-final-result.json`。
- 前一轮 lint 长时间持续分析但未出报告，且检查启动后曾补充最后一个边界用例，因此未采纳该轮作为最终证据。精确核实任务父进程及工作目录后，仅停止该旧 Wrapper 与其忙碌 Daemon；固定最终源码，使用单次 Gradle 进程、最多 4 个 worker 和 Kotlin in-process 编译重新验证，并在运行前后计算源码哈希。
- 批次 C 期间重新发现已连接手机：Android API 37、arm64，已安装应用 1.54.6。已核对与本地调试 APK 的签名兼容，录音权限为拒绝；原 APK 已备份到被忽略的 `temp/device-regression/original-base.apk`，不含用户数据。
- 设备测试包最初两次安装返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，均已恢复原 APK。用户说明未及时查看手机并明确授权重试后，改为先显示新增测试包安装确认；安装成功，六项现有设备测试全部通过，耗时 20.622 秒。
- 设备用例覆盖应用包名、音频焦点丢失、双播放器交接/拖动/变速、真实 PCM 驱动与隐藏停止分析，以及 Mesh 渲染，不验证 QQ 在线歌词与选歌列表交互；最后的 API/并行查询边界由最终 JVM 回归覆盖。既有 CPU 测试测得：10 秒 48 kHz 立体声 PCM 三频分析 CPU 55.71 ms；10 秒 30 Hz envelope CPU 1.07 ms。这是该设备的一次样本，不代表整机耗电、启动延迟或优化收益比较。
- 设备回归结束后恢复原 APK，独立检查器再次确认手机 APK SHA-256 与备份完全一致，本次测试包已移除；没有卸载主应用或清除用户数据。结果保存在被忽略的 `optimization-device-tests.log` 和 `temp/device-regression/result.json`。
- 执行宿主的 PowerShell 曾出现模块加载/退出异常；已改用独立 Python 检查器，明确核验六项测试通过、原 APK 恢复和测试包清理，未将宿主异常当作 Android 测试失败或跳过证据。
- 真机歌词 UI 和真实网络超时尚未执行。AMLL 同步 OkHttp execute 的物理请求取消绑定仍属后续独立问题，本批不声称已解决。

回退本批可还原 ResponseHandler、LyricManager、LyricPreloader 并删除新增的请求/搜索协调文件与相关测试，不涉及用户数据迁移。下一批按计划迁移播放主线程的同步设置读取。

## 2026-10-15：批次 D，播放主线程设置读取

### 修改与兼容边界

- PlayerConnection 的上一首和播放模式切换不再调用同步 DataStore getter。生产代码使用可注入 Preferences Flow 的 PlaybackSettingsActions，调用方只提交操作；工作协程挂起等待真实设置后，仍在所属主线程执行播放器操作。
- 本批采用按操作读取 `data.first()`，不建立具有临时默认值的全局缓存；DataStore 本身提供缓存。每次操作读取最新可用的持久化值，冷读取未完成时保留待执行操作，不提前按默认设置播放。两个入口共用顺序队列，连续点击依次执行，播放模式在执行时计算，避免捕获同一个旧模式。
- 已存在键、布尔值及缺失键时的播放行为不变：PreviousPlaybackKey 缺失为 true，LoopPlaybackKey 缺失为 false。后者与设置页默认显示 true 的既有差异保留并登记，不能在性能迁移中悄悄统一。
- 读取失败通过现有 reportException 诊断入口报告，不使用默认设置执行该次操作；下一次点击会重新读取。没有新增设置页面或失败弹窗。取消不转成读取失败。
- dispose 取消设置读取和全部排队操作；慢读取即使忽略取消也不能继续调用播放器。服务连接被替换或 Compose 解绑时补齐 dispose，避免旧连接的任务与监听器存留。
- AutoMix 的 suspend 音源回调改为直接挂起读取音质。MainActivity 的设备 ID 初始化改为 DataStore edit 内判断，避免同步读取并保留已有非空 ID；缺失时的 assets 读取移到 IO。其调用的 getAndroidId(context) 内部同步 getter 也改为挂起读取，原有生成和保存逻辑不变。
- 未修改 DataStore 文件名、偏好键、数据库版本、队列恢复格式、播放器切换策略及界面。通用同步 getter 仍保留，下一批按调用线程盘点；MusicService 的 Media3 同步 Resolver 与销毁时持久化不在本批机械替换。

### 验证与剩余项

- 新增 7 个 JVM 用例：延迟冷读取不阻塞其他协程且不使用临时默认值、缺失键兼容、保存后下次操作读取新值、30 次连续操作按序执行、读取失败后重试、销毁拒绝排队/迟到操作、父作用域取消不报普通错误。
- 新增设备测试使用独立缓存目录下的真实 Preferences DataStore 文件，延迟首读时验证主 Looper 仍能处理任务，操作在主 Looper 上执行，验证已有 false/true 值与持久化更新后的 true/false 值。不会访问应用的 settings 文件，结束后关闭作用域并删除测试文件。
- 最终组合验证全部通过：67 个 suite、412 个单元用例，0 失败、0 错误、0 跳过；本批新增的 7 个用例均通过。两个调试 APK 构建成功，lint 无新增问题，原有 198 条基线未扩大。Gradle 退出码 0，耗时 483.64 秒，运行前后源码哈希一致。JDK 21 和 Gradle 组合验证参数沿用批次 C。日志为被忽略的 `optimization-settings-final.log`，结果为 `temp/optimization-settings-result.json`。
- 设备专项测试包安装首次返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，未替换主应用。用户明确回复“可以重试”后，安装与专项测试均成功，Android API 37 arm64 设备报告 `OK (1 test)`，测试用时 0.043 秒。该时间只是测试框架执行时长，不是读取延迟或性能收益。日志为被忽略的 `optimization-settings-device-tests.log`。
- 测试前后均核验主应用 APK SHA-256 与原版备份相同；恢复原版 APK 成功，测试包已移除，没有清除主应用数据。新增测试只使用独立缓存文件，未操作用户的真实设置。
- 该设备测试不等于完整 PlayerConnection/Service/UI 联调；手动上一首、模式循环、冷启动队列恢复及 AutoMix 真音源仍需综合回归。不据此宣称实测启动时间或切歌延迟改善。

回退仅涉及本批四个原文件、PlaybackSettingsActions 及其两类测试；删除新增协调器前先还原 PlayerConnection 调用。本批无数据迁移。下一批 E 继续盘点页面初始化、网络构造、下载和同步媒体接口中的设置访问。

## 2026-10-15：批次 E，剩余设置读取迁移与同步边界盘点

### 修改与行为

- 移除 PlayerViewModel、PlaylistViewModel 构造时的用户 ID 同步读取；账号相关查询在原 viewModelScope 等待真实设置，不用临时空 ID 发请求。我的歌单合并设置与 Room Flow，账号 ID 改变时更新过滤结果，修复原来固定构造期 ID 的行为。
- 歌单请求读取 ID 失败通过现有 Resource.Error 或 reportException 入口报告并停止，不转成已登出账户继续请求。我的歌单订阅失败记录日志并显示空列表；现有列表 UI 没有专门的设置失败状态，本批没有新增页面。该订阅异常后需重新订阅以重试；真实账号切换期间的在途网络任务代际治理不在本批宣称已完成。
- 单曲下载的音质和目录改为挂起读取；显式音质、保存的枚举、EXHIGH 回退及目录默认值保持不变。普通音质读取失败仍按原行为回退，取消则立即传播，不能继续用回退音质下载。
- 自动缓存配置改为真实设置首值中的一次快照，入队前重新读取目录；本地文件存在性判断在 IO。未改变播放次数、阈值、音质枚举或本地副本去重规则。
- 首页请求 DSL 不再持有 AppContext 或读取设置，由仓库异步取得 Android ID 再传入；请求协议字段仍为 android_id，空字符串不重新生成，缺失键沿用原回退生成逻辑。构造器与 DSL 参数为仓库内源码 API 调整，调用者同步迁移，不改变网络字段。
- 未使用的 DownloadManager.isSongDownloaded 保留入口并改成 suspend，移除 Room runBlocking，普通文件检查在 IO；NavigationBuilder 仅删除未使用的 runBlocking 导入。
- 未修改真实账号、Cookie、偏好键、数据库或持久化文件格式。完整盘点见 [synchronous-settings-inventory.md](synchronous-settings-inventory.md)。本批移除 9 处业务同步设置调用，保留拦截器 3 处和 Media3 Resolver 1 处；生命周期最终快照刷盘仍为明确待处理风险。

### 验证与限制

- 新增 13 个 JVM 用例：账号真实首值等待、过滤随账号/歌单变化、每次操作读取当前 ID、读取失败与取消；下载音质显式优先/原枚举映射/无效值及普通失败回退/取消；纯首页请求构造、空 ID、DSL 覆盖与协议字段。
- 新增 1 项设备专项：独立真实 Preferences DataStore，在 Main 上延迟读取，核验设置就绪前不按临时 ID 过滤，持久化 A→B→移除 ID 时列表更新，并验证挂起当前 ID 读取。不使用用户真实账号文件。
- 首轮组合验证启动后复核了请求协议注解，将新测试中的断言字段纠正为真实的 android_id。因此该轮输入哈希变化，不作为最终冻结源码证据；最终将用同一命令再跑稳定输入，不能把早启动检查混作最终验证。
- 首轮 Gradle 本身全部通过，70 个 suite、425 个单元用例全部通过（0 失败、0 错误、0 跳过），包括本批新增的 13 个；lint 无新增问题，两个调试 APK 构建成功，耗时 460.12 秒。因测试断言在启动后修正，校验脚本按预期拒绝将输入变化的该轮作为最终冻结证据，记录保存在被忽略的 `optimization-preferences-initial.log` 与 `temp/optimization-preferences-initial-result.json`。
- 固定源码后的最终组合验证通过，Gradle 退出码 0，前后源码哈希一致，耗时 35.55 秒；Gradle 按输入追踪复用已有测试/lint/打包产物（94 个任务 up-to-date），425 个测试结果保持通过，原有 198 条 lint 基线未修改。JDK 21、Gradle 命令与参数沿用批次 C/D。最终日志与结果为被忽略的 `optimization-preferences-final.log`、`temp/optimization-preferences-result.json`。
- 尝试运行新增设备专项时，ADB 返回 `device '26506685' not found`；安装前检查即停止，未安装测试包、未替换主应用，也未访问真实账号数据。再次查询设备列表为空。用户随后明确选择“本批先跳过真机测试”，本批不再尝试安装或等待重连。新增设备测试仅完成编译，按用户选择暂缓执行，保留至后续综合回归补测，不能记为真机通过。上一批 D 的真机结果不替代本批专项验收。
- 真实首页网络、账号登录切换、下载入队、自动缓存和进程恢复尚未联调；剩余同步接口也尚未完成耗时采样。没有据此报告启动/切歌/请求准备的性能收益。

回退：还原本批 ViewModel/overlay、AutomaticCacheController、HomeRepository/请求 DSL、DownloadManager/导航导入，并删除新增读取函数与测试；先还原调用者再删除函数，首页 DSL 参数与调用一并回退。本批无用户数据迁移。下一批 F 进入只读播放状态与生命周期治理；H/K 保留同步媒体、刷盘和耗时测量事项。
