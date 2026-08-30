# VitalHub - Agent Context

## 1. 项目定位

- 项目职责：VitalHub 健康数据采集 Android 应用。
- 主要工程形态：Kotlin、Jetpack Compose 与 Activity + Fragment 混合的多模块 Android 工程。
- 本仓库负责：采集任务入口、问卷、BLE 设备连接、采集记录、异步分析，以及应用壳层导航。

## 2. Agent 工作准则

### 修改前

- 先阅读本文件、`ARCHITECTURE.md` 与命中的 `docs/` 索引；涉及模块细节时再阅读对应 Gradle 文件、路由和入口代码。
- 通过 `settings.gradle.kts` 和模块 `build.gradle.kts` 确认模块边界与依赖，不仅依据目录推断。
- 变更路由、采集链路或用户资料时，先追踪 `Routes`、`Navigator`、目标 Activity、Fragment 和 ViewModel。

### 修改中

- 优先最小、局部且可验证的改动；不做无关重构、依赖升级或模块重组。
- 模块和 SDK 依赖禁止使用 Gradle `api`。默认使用 `implementation` 隔离实现；仅当依赖只用于编译、并且运行时由明确的应用或宿主模块负责提供时，才使用 `compileOnly`。使用 `compileOnly` 前必须确认运行时提供者在最终 APK 中以非 `compileOnly` 方式被打包，并验证所有直接消费者的编译、测试和实际运行链路均不缺类。
- 业务 feature 之间不得新增直接 Gradle 依赖。跨模块页面交互使用 ARouter：路径、参数、导航策略与 `Navigator` 集中定义在 `:core:navi`，调用方复用 `Navigator`，目标 feature 以 `@Route` 暴露入口；禁止直接构造、依赖或调用另一个 feature 的页面实现。
- 将 `:provider:*` 作为跨业务能力的优先抽象层：先定义稳定的模型、接口与回调，再在所属业务 feature 实现。**本项目新增或重构的跨模块 Provider 一律采用 ARouter Provider：接口继承 `IProvider`，实现以唯一 `@Route` 注册；调用方只依赖接口，并通过 `ARouter.navigation(接口::class.java)` 或 `@Autowired` 注入获取服务。**
- Provider 业务接口的新增实现类及 Kotlin 文件优先命名为 `XxxProviderImpl` / `XxxProviderImpl.kt`，使接口与实现关系可直接识别；只有多个实现确实需要区分职责时才增加业务前缀。未经明确需求，不为统一命名重命名既有实现。
- 禁止为 Provider 自建全局注册表、Service Locator，或在 `Application` 中直接引用 feature 的实现类并手动 `install`/注入。这些做法会绕过 ARouter 的服务发现、实现替换与模块边界；除非用户明确指定兼容某个既有遗留实现，否则不得以“实现更简单”作为例外。
- 错误复盘：把接口放进 `:provider:*` 但不继承 `IProvider`、实现不加 `@Route`、调用方不经 `ARouter.navigation(...)`，只是普通接口加手动注册，**不构成 ARouter Provider**；不得以模块固定、非动态化或当前只有一个实现为由规避该约束。
- Provider 的 `init(context)` 仅做轻量、线程安全的初始化且只保存 `applicationContext`；存储、Repository 等实现细节留在所属 feature 内创建。不得持有 `Activity`、`View` 或大对象，也不得执行网络/耗时 IO。跨模块回调使用 provider 中定义的接口，避免把 Lambda、页面对象或不可序列化状态塞入路由参数。
- **跨模块数据传递按生命周期选型：**凡是可持久化、可由现有或新增 Provider 查询/恢复的业务数据（包括实体、状态标识和可推导的页面模式），目标模块必须通过 `ARouter.navigation(Provider::class.java)` 自行读取，禁止由上游通过路由参数转发。路由参数仅用于一次性、纯内存的导航上下文，且该上下文不能由 Provider 恢复或推导；设计前先检索可复用的 Provider。
- 新增能力前先检索并优先复用现有 provider、common 或业务模块能力，避免重复实现；仅在能力具有稳定跨模块价值时新增 provider/common 契约。
- 首页通过 Fragment 作为 ARouter 入口并由 `MainActivity` 承载；其他业务 feature 通过 Activity 作为跨模块 ARouter 入口、Fragment 作为内部页面和 Compose 生命周期容器。Activity 内部切换仍通过 ARouter 解析 Fragment，再由 Activity 提交事务；不要引入 XML/ViewBinding 页面路径。
- 新增或实质改版业务页面时，默认遵循 `docs/coding_standards/visual-design.md` 的健康采集页风格，并以 **414dp 宽**作为设计与视觉验收基准。优先复用 `VitalHubTheme`、`VitalColors`、`FlowPage`、`InfoCard`、`FlowButton` 等公共 UI；布局仍须使用约束式、可伸缩的 Compose 写法，不能把 414dp 写成设备的固定页面宽度或造成窄屏裁切。
- 不将尚未证实的 BLE 协议、数据持久化或服务端行为写成既有事实。

### 修改后

- 至少执行与改动范围相称的验证；无法构建时说明未验证范围与原因。
- Provider 变更除编译外，必须验证实现模块已启用 ARouter KAPT、生成路由表中包含该 Provider，并在调用方通过 `ARouter.navigation(接口::class.java)` 成功获取；同时检查 release 混淆保留规则。不得只验证页面路由可达就认定 Provider 可用。
- 页面新增或修改必须完成端到端验证，不能只检查编译：从真实入口验证路由/参数、页面状态、主要用户操作及预期结果，并覆盖与改动相关的空态、失败态或返回路径。随后在 414dp 基准下对照需求与视觉设计检查页面层级、文案、交互可达性和样式；具备条件时提供截图或测试证据。受环境、设备或外部服务限制时，明确列出未验证的链路与原因。
- 若改动影响模块边界、路由、核心业务流程、术语或通用生成约束，同步更新命中的上下文文档。
- 输出改动摘要、关键文件、验证结果和遗留风险。

## 3. 文档读取规则

### 读取顺序

1. 根目录 `AGENTS.md`
2. 当前修改目录最近的 `AGENTS.md`（当前仅根目录）
3. 根目录 docs 索引命中的文档
4. 模块 docs 索引命中的文档（模块级上下文尚未建立时，转读相邻代码和 `ARCHITECTURE.md`）
5. 必要时代码上下文

### 关键词路由

| 场景/关键词 | 优先读取 | 其次读取 |
|---|---|---|
| 构建、模块、Gradle、依赖 | `docs/architecture/index.md` | 对应模块 `build.gradle.kts` |
| 路由、底栏、标题栏、返回栈 | `docs/architecture/app-shell.md` | `ARCHITECTURE.md`、`core/navi/.../` |
| 采集、问卷、设备、分析、用户资料 | `docs/glossary/index.md` | `ARCHITECTURE.md`、相关 feature 代码 |
| 命名、Compose、跨模块调用、文档同步 | `docs/coding_standards/index.md` | 本文件工作准则 |
| 新页面、页面样式、414dp、视觉适配 | `docs/coding_standards/visual-design.md` | `core/common/.../ui/BaseFlowFragment.kt` |

### 冲突处理

- 用户当前明确指令优先级最高。
- 更近目录的 `AGENTS.md` 优先于上级目录。
- 模块 docs 对本模块细节优先于根 docs。
- 根 docs 对跨模块公共约定优先于模块局部说明。

## 4. 术语读取规则

### 读取顺序

1. 根目录 `docs/glossary/index.md`
2. 相关模块 `docs/glossary/index.md`
3. 命中的术语详情文件
4. 相关功能文档 `docs/biz/features/<feature>.md`

模块级 glossary 和功能文档尚未建立；新增稳定的业务术语或完整功能链路时，应在 owner 模块中按该路径补齐。

### 术语分层

| 类型 | 索引位置 | 详情位置 | 说明 |
|---|---|---|---|
| 跨模块共享术语 | `docs/glossary/index.md` | `docs/glossary/<term-slug>.md` | 全局统一语义 |
| 模块局部术语 | `<module>/docs/glossary/index.md` | `<module>/docs/glossary/<term-slug>.md` | 模块内特殊含义 |
| 功能链路术语 | `<module>/docs/biz/features/<feature>.md` | 相关 glossary 详情文件 | 术语在具体链路中的使用方式 |

## 5. 文档维护规则

| 改动类型 | 必查文档 | 更新条件 |
|---|---|---|
| 模块依赖变化 | `docs/architecture/index.md` | 依赖方向、模块层级或影响面变化 |
| 新增/调整模块 | `AGENTS.md` | 模块索引变化 |
| 编码准则变化 | `docs/coding_standards/index.md` | 代码生成规则、通用封装或禁用方式变化 |
| 新增业务概念 | 根或模块 `docs/glossary/index.md` | 术语影响跨任务理解 |
| 修改功能链路 | 模块 `docs/biz/features/<feature>.md` | 流程、状态、入口或降级变化 |
| 修改模块边界 | 模块 `AGENTS.md` | 职责、入口或关键类变化 |

## 6. 根目录文档索引

| 类型 | 场景 | 路径 |
|---|---|---|
| 架构 | 构建环境、模块依赖、工程分层 | `docs/architecture/index.md` |
| 应用壳与导航 | Activity、ARouter、Compose 壳、返回栈 | `docs/architecture/app-shell.md` |
| 编码准则 | 代码生成、注释、兼容性、通用约束 | `docs/coding_standards/index.md` |
| 视觉设计 | 新增页面的健康采集风格、布局与 414dp 基准 | `docs/coding_standards/visual-design.md` |
| 全局术语 | 跨模块术语命中和详情路由 | `docs/glossary/index.md` |
| 既有架构说明 | 业务流程与推荐数据层边界 | `ARCHITECTURE.md` |

## 7. 模块索引

| 模块 | 职责 | 依赖层级 | Agent 文档 |
|---|---|---|---|
| `:app` | Application、首页 Activity、应用壳与顶级导航 | 组装层 | 根 `AGENTS.md` |
| `:core:common` | 跨模块通用模型与公共 UI | 基础层 | 根 `AGENTS.md` |
| `:core:navi` | 路由契约、导航宿主接口、返回策略与导航去重 | 基础层 | 根 `AGENTS.md` |
| `:core:permission` | 可注入的运行时权限契约、检查、申请、端内兜底弹窗与设置页跳转 | 基础层 | 根 `AGENTS.md` |
| `:core:storage` | 通用键值存储抽象及 SharedPreferences/MMKV 实现 | 基础层 | 根 `AGENTS.md` |
| `:foundation:bluetooth` | 经典蓝牙与 BLE 的扫描、连接、读写及事件回调基础能力 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-waveform-ui` | 物理心电图纸、实时波形环形缓冲、扫屏/滚动与 ECG/呼吸 Compose 组件 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-api` | 设备 SDK、会话、聚合帧、命令和状态稳定契约 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-transport` | 蓝牙回调到有序字节流/挂起写入的适配 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-protocol` | 环形缓冲、拦截器拆包、校验、连续性和聚合帧解析 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-command` | 有界优先级队列、单飞指令、回执和超时重试 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-storage` | 解析后聚合帧的异步文件记录 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-waveform` | ECG/呼吸波形投影和慢消费者隔离 | 基础层 | 根 `AGENTS.md` |
| `:foundation:device-sdk` | 设备能力总壳、会话编排和自动分发 | 基础层 | 根 `AGENTS.md` |
| `:provider:user` | 用户资料的数据模型与提供者契约 | Provider 层 | 根 `AGENTS.md` |
| `:provider:collection` | 采集流程进度的数据模型与提供者契约 | Provider 层 | 根 `AGENTS.md` |
| `:provider:device` | 最近成功连接设备的数据模型与提供者契约 | Provider 层 | 根 `AGENTS.md` |
| `:feature:home` | 采集任务入口与任务列表 | 业务 feature | 根 `AGENTS.md` |
| `:feature:user` | 用户资料编辑与本地资料实现 | 业务 feature | `feature/user/AGENTS.md` |
| `:feature:questionnaire` | 采集前后问卷 | 业务 feature | 根 `AGENTS.md` |
| `:feature:collection` | BLE 权限、扫描、连接、实时预览、片段、连续记录与采集进度 Provider 实现 | 业务 feature | 根 `AGENTS.md` |
| `:feature:analysis` | 异步分析任务及结果 | 业务 feature | 根 `AGENTS.md` |
