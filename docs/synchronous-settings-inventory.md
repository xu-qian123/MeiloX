# 同步设置读取盘点（2026-10-15，批次 E）

此为合并上游 1.55.0 前的历史盘点；2026-10-16 的合并调整见 [upstream-1.55.0-merge.md](upstream-1.55.0-merge.md)。后续批次须基于合并后的调用链重新核实数量和线程。

范围：`app/src/main/java` 的 DataStore 同步 getter、preference/enumPreference 委托及 runBlocking。检索包含调用表达式、工具导入、委托用法和残余 runBlocking；不能仅凭函数名含 suspend 判断内部是否阻塞。

## 本批迁移

| 路径 | 原调用线程/依据 | 处理与兼容约定 |
| --- | --- | --- |
| PlayerViewModel 初始化 userId | Compose/Hilt 页面创建路径，构造时执行 | 删除构造期读取；我的歌单合并真实设置 Flow 与 Room Flow，等待二者首值，账号更新重新过滤。初始列表保持原有空列表 |
| PlayerOverlayHandler 添加歌单 | UI 点击 | 只提交同步请求；PlayerViewModel 在 viewModelScope 中等待真实当前 ID，缺失 ID 时不发账号网络请求。读取失败使用现有 Resource.Error，取消继续传播 |
| PlaylistViewModel 初始化 userId | 页面 ViewModel 构造路径 | 将读取移到 getAllMePlaylist 的协程中，一次操作固定同一个 ID；读取失败报告诊断并停止，不能按临时空 ID 执行查询 |
| PlayerViewModel 下载音质/目录 | viewModelScope 默认 Main | 挂起读取；显式音质仍优先且不读取保存的音质，未保存/无效值/普通读取失败继续使用 EXHIGH；取消不再作为普通失败回退继续下载。路径仍在解析音源成功后读取，默认目录不变 |
| AutomaticCacheController | MusicService 的 Main scope，切歌后 delay 调用 | enabled/threshold/quality 从同一次挂起读取获得，保持原有阈值、枚举回退；入队前重新读取目录。文件存在性检查切到 IO；播放次数记录与本地副本去重逻辑保留 |
| 首页请求构造 DSL | HomeRepository 挂起方法调用，调用者可能在 Main；原构造函数间接读盘 | 仓库挂起读取 AndroidIdKey，然后显式传入纯 DSL。已有非空及空字符串原样保留；仅缺失键时沿用原有无参 getAndroidId() 生成回退，不新增持久化写入。请求字段名仍为 android_id |
| DownloadManager.isSongDownloaded | 仓库内未发现调用者，原为可阻塞的同步公共函数 | 改为 suspend，Room 首值异步等待；普通文件 exists 在 IO，content URI 判定不变。未删除入口 |
| NavigationBuilder | 只有未使用的 runBlocking import | 删除无用导入，不修改导航行为 |

本批移除 9 处实际同步设置调用；迁移后业务代码仍有 4 处直接调用，均位于下列同步接口。`DataStore.kt` 的两个同步 getter 定义及两个委托的内部调用不计入业务调用数；仓库未找到委托调用者。

## 保留的同步边界

| 位置 | 原因与线程依据 | 后续约束与测量 |
| --- | --- | --- |
| NeteaseInterceptor.intercept：DeviceIdKey、CookieKey、DebugKey，共 3 处 | OkHttp Interceptor 是同步接口；当前 Retrofit suspend 服务使用异步请求调度。拦截器也可能被未来同步 execute 调用，不能仅凭 Interceptor 类型保证后台线程 | 保留每次请求读取已保存认证信息的行为，不能改成初始空 Cookie/旧账号缓存。网络治理时评估就绪后的认证快照；新增直接 execute 调用需证明线程。当前未测这些读取的等待时间，不声称无开销；阶段 K 采集请求准备耗时，最迟 2026-10-29 复核 |
| MusicService ResolvingDataSource.Resolver：MusicQualityKey，1 处 | Media3 数据源 open/resolve 是同步接口，在媒体加载路径执行；同一数据源还同步等待本地路径和远程解析 | 本批保留音质与缓存表示绑定，避免 seek 字节偏移落入不同音质。阶段 H 提取媒体源职责时复核线程与失败处理，阶段 K 对缓存/网络准备分别采样，最迟 2026-10-29 复核 |
| 同一 Resolver 的 Room 路径与音源解析，2 处 runBlocking | 同步接口需要在返回前获得路径/URI；不是设置 getter | 不能只把任务 launch 后返回未就绪 URI；随媒体源职责处理，测量与上项一致 |
| MusicService.persistPlaybackSnapshotBlocking，1 处 runBlocking(IO) | onTaskRemoved/onDestroy 必须处理最终状态保存；调用线程仍会等待 IO，属于已知主线程阻塞风险 | 不在本批改成可能随服务销毁丢失的 fire-and-forget。阶段 H 评估周期快照/增量检查点与最终刷盘边界，再验证进程终止恢复；阶段 K 记录耗时与尾延迟，最迟 2026-10-29 复核 |
| DataStore.kt 同步工具及未使用委托 | 仍服务上面的同步接口；没有全局替换所有调用 | 新增 UI/普通挂起路径必须使用 Flow/挂起读取。未把 getter 改成缓存默认值，也未删除现有持久化键 |

以上线程依据是代码调用链核查，尚不是运行时线程/耗时测量。真实媒体、认证网络与生命周期刷盘的测量仍未完成；批次 E 不据此宣称已移除全项目全部阻塞。

## 验证边界

JVM 验证生产实际使用的设置读取和过滤函数，以及无需 AppContext 的请求 DSL。设备专项使用独立的真实 Preferences DataStore 文件，在 Main Looper 延迟首读并切换账号值；不读取或写入用户实际账号设置。账号真实登录切换、首页网络、真实下载入队、自动缓存和进程恢复仍需综合回归。执行结果见 [optimization-baseline.md](optimization-baseline.md)。
