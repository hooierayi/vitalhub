# Coding Standards

## 1. 代码生成总则

- 命名规范：遵循现有 Kotlin 与 Compose 命名；路由路径、参数名和跨模块模型保持稳定且集中定义；新增 Provider 业务接口实现类及文件优先使用 `XxxProviderImpl` / `XxxProviderImpl.kt`。
- 注释规范：为跨模块契约、生命周期边界、状态机或非直观的平台兼容处理保留简洁原因说明；不为显而易见代码添加重复注释。
- 兼容性要求：遵守 `minSdk 24`，涉及权限、系统栏、BLE 或 Fragment 行为时确认目标 Android 版本差异。
- 禁止事项：不新增 feature-to-feature Gradle 依赖；不将业务 feature 实现提前下沉到 common；不无关升级版本目录中的依赖。
- 视觉设计：新增或实质改版业务页面以 `visual-design.md` 为默认风格；以 414dp 宽为设计基准，同时保持响应式布局。

## 2. 通用工程约束

| 场景 | 准则 | 说明 |
|---|---|---|
| 跨模块页面 | 使用 `:core:navi` 中的 ARouter、`Routes` 与 `Navigator` | feature 之间只通过稳定路由契约协作，不直接引用目标页面实现 |
| 跨模块业务能力 | 优先在 `:provider:*` 定义模型、接口与契约 | 具体实现留在所属 feature；需要运行时发现时才以 ARouter `IProvider` + `@Route` 注册 |
| 页面实现 | Fragment 作为路由入口，ComposeView 承载 Compose UI | 与当前应用壳保持一致 |
| 状态 | 页面状态归属 ViewModel，避免依赖 Fragment 临时生命周期 | 长连接/跨页面状态应设计明确所有者 |
| 构建配置 | 仅在需求明确时修改 Gradle、Manifest 或版本目录 | 这些改动覆盖面广 |
| 模块与 SDK 依赖 | 默认使用 `implementation`；运行时由明确宿主打包时才使用 `compileOnly` | 禁止使用 `api`；`compileOnly` 必须确认最终 APK 的提供者、直接消费者编译和运行链路均完整 |
| 依赖 | 先复用已有 `:provider:*`、`:core:navi`、`:core:common` 或业务模块能力 | 仅确有稳定跨模块价值时新增 provider/core 契约，避免重复实现 |

## 3. 语言与框架约束

| 类型 | 准则 | 示例/说明 |
|---|---|---|
| Kotlin | 保持现有 Kotlin 1.9 / JVM 1.8 兼容范围 | 不引入超出当前配置的语言或字节码要求 |
| Gradle 依赖 | 改动后检查依赖图并编译受影响模块；`compileOnly` 额外验证宿主运行时打包 | 依赖传递暴露、仅靠 IDE 编译成功或运行时缺类 |
| Compose | 业务 UI 放入 feature，使用当前 Compose BOM 管理的依赖 | 不为单页回退到 XML 布局 |
| 页面视觉 | 遵循 `visual-design.md`，优先复用 common UI 组件与 `VitalColors` | 复制页面样式、硬编码独立色值或把 414dp 固定为页面宽度 |
| Fragment | 用于 ARouter 入口与生命周期容器 | FragmentTransaction 由 app 壳层集中处理 |
| ARouter | 路径、参数和导航封装放在 common | 路径变更须同步检查调用方和目标入口 |
| Provider 实现 | `XxxProviderImpl.kt` 与 `class XxxProviderImpl` | 无实际多实现区分需求时使用存储或 SDK 名称替代业务实现名 |

## 4. 优先复用规则

| 场景 | 优先使用 | 禁止/避免 |
|---|---|---|
| 页面跳转 | `Navigator` | feature 间直接构造或引用对方 Fragment |
| 导航参数 | `RouteArgs`、`QuestionnairePhase`、`CollectionMode` | 散落的字符串字面量 |
| 顶栏和底栏 | `AppBarDestination`、`BottomNavigationDestination` 与 app 壳 | 业务页面自行复制应用级导航 UI |
| 用户资料 | `:provider:user` 的公开契约与 `:feature:user` 的实现 | 在多个 feature 内复制资料存储逻辑 |
| ARouter Provider | `IProvider` 接口、`@Route` 实现和 `navigation(接口::class.java)` | 用页面路由承载业务服务调用，或让调用方依赖具体实现 |
| 页面骨架 | `FlowPage`、`InfoCard`、`FlowButton`、`SectionTitle` | 重复实现相同容器、卡片与按钮样式 |

## 5. 文档同步规则

| 改动类型 | 需要同步的文档 |
|---|---|
| Gradle 模块或依赖 | `docs/architecture/index.md`、`AGENTS.md` |
| 路由和应用壳 | `docs/architecture/app-shell.md` |
| 稳定业务术语 | `docs/glossary/index.md` 及详情文件 |
| 页面视觉或公共 UI | `docs/coding_standards/visual-design.md` |
| 模块内部的稳定流程 | 对应模块 `docs/biz/features/`（首次改动时创建） |
