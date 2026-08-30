# 应用壳与路由

## 1. 入口与职责

- `VitalHubApplication` 初始化 ARouter；仅在 debuggable 应用中开启 ARouter 日志和调试。
- `MainActivity` 负责首页 edge-to-edge 窗口、应用标题栏、底部导航和首页 Fragment 返回栈。
- 首页以 Fragment 作为 ARouter 入口；其他 feature 以 Activity 作为跨模块 ARouter 入口，并在 Activity 内通过 ARouter 解析 Fragment。
- 需要运行时权限的 Activity 路由由 app 层 `PermissionRouteInterceptor` 统一守卫。权限定义、受保护路径和弹窗实现均在应用组装时注入 `:core:permission`；feature 只在 Manifest 声明权限，不能在页面内直接申请。

## 2. 路由契约

| 组件 | 位置 | 约束 |
|---|---|---|
| 路径与参数常量 | `core/navi/.../Routes.kt` | 新增跨模块入口先定义稳定路径与参数名 |
| 跳转封装与返回策略 | `core/navi/.../Navigator.kt`、`BaseFlowActivity` | 跨模块调用复用此处封装，避免在 feature 间直接引用类 |
| 业务能力契约 | `:provider:*` | 跨业务模型、接口和回调优先定义在 provider，调用方只依赖契约 |
| ARouter Provider 实现 | 具体业务 feature | 跨模块 Provider 必须实现 `IProvider` 并通过唯一 `@Route` 注册 |
| 事务宿主 | `MainActivity` 与 `BaseFlowActivity` 的 `FlowNavigationHost` 实现 | 各 Activity 统一管理自己的 FragmentTransaction、动画和返回栈 |
| 顶级导航标记 | `BottomNavigationDestination` | 顶级页面实现后，应用壳显示底栏 |
| 标题栏元数据 | `AppBarDestination` | 页面通过接口声明标题和返回按钮，不重复绘制标题栏 |

当前用户资料编辑 Activity 路由为 `/user/edit`，由 `:feature:user` 的 `UserActivity` 注册；内部 Fragment 路由为 `/user/edit/content`。采集 Activity 路由为 `/collection/flow`，内部采集流程首页和采集 Fragment 分别为 `/collection/flow/home` 与 `/collection/main`。首页“采集前问卷、数据采集、采集后问卷”三步流程由 `CollectionFlowProvider` 的 `/collection/flow/service` 服务读取和更新；设备连接属于数据采集内部瞬态，不单独作为持久化检查点。

业务 Activity 内进入二级 Fragment 时，将当前页面隐藏并压入 Fragment 返回栈，而不是通过 `replace` 销毁其 View；返回时恢复原 Fragment、ViewModel 与 Compose 状态，避免采集流程首页重新扫描或丢失已展示设备。

### Provider 服务边界

- 页面跳转与业务能力调用分离：跨模块页面通过 Activity 路由，Activity 内部页面通过 Fragment 路由，跨模块能力通过 provider 接口。业务调用方不得依赖另一个 feature 的实现类或页面路径细节。
- provider 模块只定义继承 `IProvider` 的公开接口及跨模块回调；具体 feature 实现该接口并以唯一 `@Route` 注册。调用方通过 `ARouter.navigation(Service::class.java)` 或 `@Autowired` 获取接口；使用 `@Autowired` 时必须调用 `ARouter.getInstance().inject(this)`。
- ARouter Provider 视为应用级服务：`init(context)` 只做轻量、线程安全的初始化，保存 `applicationContext`，不持有界面对象或执行网络/耗时 IO。
- 服务可能缺失、模块可选或版本不一致时，调用方必须允许空值或处理获取失败并给出降级；不要假定服务始终存在。
- 跨模块数据按生命周期选择载体：可持久化、可由 Provider 查询/恢复的业务数据（包括实体、状态标识和可推导的页面模式）必须由目标模块自行通过 Provider 获取，不通过路由参数转发；路由参数仅承载不可恢复的一次性内存导航上下文。
- 不将 `Activity`、`View`、大对象、不可序列化状态或临时 Lambda 作为路由参数跨模块传递；需要回调时，将回调接口定义在 provider 契约中。
- 当前 `UserInfoProvider` 以 `/user/service` 注册，调用方通过 `ARouter.navigation(UserInfoProvider::class.java)` 获取；不维护手写注册表或由 `Application` 手动安装实现。
- 当前 `CollectionFlowProvider` 以 `/collection/flow/service` 注册，由首页和流程模块通过 `ARouter.navigation(CollectionFlowProvider::class.java)` 获取；Provider 只保存三项业务流程检查点，不保存设备连接和采集中的硬件瞬态。

## 3. UI 壳约束

- 顶级底栏目前包含采集、记录、报告、我的；后 3 项目前由 app 内占位页面承载。
- 顶级 Tab 不进入业务返回栈；子流程默认入栈并隐藏底栏。
- 标题栏返回与系统返回共用 Activity 返回栈策略：片段采集、连续记录在 `CollectionFlowActivity` 内回到实时预览；倒计时完成进入上传与分析页时，等待 `AnalysisActivity` 到达后销毁 `CollectionFlowActivity`；上传与分析页的标题栏返回、系统返回及“回首页”操作直接恢复首页，“填写采集后问卷”在后问卷到达后移除 `AnalysisActivity`；问卷返回通过结束 `QuestionnaireActivity` 直接恢复其下的页面，其他子流程按所属 Activity 的 Fragment 返回栈逐级回退。
- 所有新业务页面维持 Activity + Fragment + ComposeView 模式，首页仍由 MainActivity 承载 Fragment，Compose 内容在 feature 模块内实现。
- 跨业务 Activity 的打开和关闭动画由应用 Theme 与 `FlowActivityTransitions` 统一提供；前进从右侧进入，返回向右退出，窗口保持不透明以避免前后页面重叠显影，feature 不单独覆盖窗口动画。
- 顺序流程进入下一业务 Activity 时，等待 ARouter 确认目标已到达后再结束当前业务 Activity；权限拒绝或导航中断时保留当前页。后问卷完成后只结束 `QuestionnaireActivity`，直接恢复栈中的既有首页，不再次导航或重建首页。

## 4. 变更风险

- 路径或参数名变更会导致 ARouter 运行期无法解析或读取参数失败。
- ARouter Provider 变更还需确认各 Android 模块的 KAPT 配置、生成路由表与 release 混淆保留规则，否则服务可能仅在发布版不可用。
- Fragment 是否实现顶级/标题栏接口会直接改变底栏与标题栏可见性。
- 调整系统栏、过渡动画或返回栈需要在真实设备上验证页面切换和系统返回。
- ARouter 1.5.2 的 Fragment 路由自动走 green channel，不执行拦截器；需要权限的流程必须把守卫配置在 Activity 外部路由上。修改拦截逻辑时需验证授权、拒绝、设置页返回和重复导航均不会丢失或重复展示页面。
