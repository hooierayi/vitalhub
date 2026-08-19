# 应用壳与路由

## 1. 入口与职责

- `VitalHubApplication` 初始化 ARouter；仅在 debuggable 应用中开启 ARouter 日志和调试。
- `MainActivity` 是唯一 Activity，负责 edge-to-edge 窗口、应用标题栏、底部导航和 Fragment 返回栈。
- feature 的 Fragment 通过 `@Route` 提供 ARouter 入口，并以 `ComposeView` 承载业务界面。

## 2. 路由契约

| 组件 | 位置 | 约束 |
|---|---|---|
| 路径与参数常量 | `core/navi/.../Routes.kt` | 新增跨模块入口先定义稳定路径与参数名 |
| 跳转封装与返回策略 | `core/navi/.../Navigator.kt`、`FlowBackPolicy` | 跨模块调用复用此处封装，避免在 feature 间直接引用类 |
| 业务能力契约 | `:provider:*` | 跨业务模型、接口和回调优先定义在 provider，调用方只依赖契约 |
| ARouter Provider 实现 | 具体业务 feature | 跨模块 Provider 必须实现 `IProvider` 并通过唯一 `@Route` 注册 |
| 事务宿主 | `MainActivity` 的 `FlowNavigationHost` 实现 | 统一管理 FragmentTransaction、动画和返回栈 |
| 顶级导航标记 | `BottomNavigationDestination` | 顶级页面实现后，应用壳显示底栏 |
| 标题栏元数据 | `AppBarDestination` | 页面通过接口声明标题和返回按钮，不重复绘制标题栏 |

当前用户资料编辑路由为 `/user/edit`，由 `:feature:user` 的 `UserInfoEditFragment` 注册；首页通过 `Navigator.editUserInfo` 进入该子流程。首页四步采集流程由 `CollectionFlowProvider` 的 `/collection/flow/service` 服务读取和更新，不作为路由参数传递。

### Provider 服务边界

- 页面跳转与业务能力调用分离：页面通过路径路由，跨模块能力通过 provider 接口。业务调用方不得依赖另一个 feature 的实现类或页面路径细节。
- provider 模块只定义继承 `IProvider` 的公开接口及跨模块回调；具体 feature 实现该接口并以唯一 `@Route` 注册。调用方通过 `ARouter.navigation(Service::class.java)` 或 `@Autowired` 获取接口；使用 `@Autowired` 时必须调用 `ARouter.getInstance().inject(this)`。
- ARouter Provider 视为应用级服务：`init(context)` 只做轻量、线程安全的初始化，保存 `applicationContext`，不持有界面对象或执行网络/耗时 IO。
- 服务可能缺失、模块可选或版本不一致时，调用方必须允许空值或处理获取失败并给出降级；不要假定服务始终存在。
- 跨模块数据按生命周期选择载体：可持久化、可由 Provider 查询/恢复的业务数据（包括实体、状态标识和可推导的页面模式）必须由目标模块自行通过 Provider 获取，不通过路由参数转发；路由参数仅承载不可恢复的一次性内存导航上下文。
- 不将 `Activity`、`View`、大对象、不可序列化状态或临时 Lambda 作为路由参数跨模块传递；需要回调时，将回调接口定义在 provider 契约中。
- 当前 `UserInfoProvider` 以 `/user/service` 注册，调用方通过 `ARouter.navigation(UserInfoProvider::class.java)` 获取；不维护手写注册表或由 `Application` 手动安装实现。
- 当前 `CollectionFlowProvider` 以 `/collection/flow/service` 注册，由首页和流程模块通过 `ARouter.navigation(CollectionFlowProvider::class.java)` 获取；Provider 只保存流程检查点，设备连接和采集的硬件瞬态在应用恢复时回退为重新连接设备。

## 3. UI 壳约束

- 顶级底栏目前包含采集、记录、报告、我的；后 3 项目前由 app 内占位页面承载。
- 顶级 Tab 不进入业务返回栈；子流程默认入栈并隐藏底栏。
- 标题栏返回与系统返回共用 `FlowBackPolicy`：片段采集、连续记录均回到实时预览，采集后问卷均回到首页；其他子流程按 Fragment 返回栈逐级回退。`MainActivity` 仅执行策略结果和 FragmentTransaction。
- 所有新业务页面维持 Fragment + ComposeView 模式，Compose 内容在 feature 模块内实现。

## 4. 变更风险

- 路径或参数名变更会导致 ARouter 运行期无法解析或读取参数失败。
- ARouter Provider 变更还需确认各 Android 模块的 KAPT 配置、生成路由表与 release 混淆保留规则，否则服务可能仅在发布版不可用。
- Fragment 是否实现顶级/标题栏接口会直接改变底栏与标题栏可见性。
- 调整系统栏、过渡动画或返回栈需要在真实设备上验证页面切换和系统返回。
