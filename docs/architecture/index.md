# Architecture

## 1. 构建环境

### Gradle

- Gradle Wrapper：`gradle/wrapper/gradle-wrapper.properties`；具体版本见该文件。
- Android Gradle Plugin：`8.4.0`。
- Gradle 配置语言：Kotlin DSL。
- 根构建文件：`build.gradle.kts`。
- settings 文件：`settings.gradle.kts`。

### Android

- compileSdk：34。
- minSdk：24。
- targetSdk：34（`:app`）。
- Java source/target compatibility：1.8。
- Kotlin JVM target：1.8。

### Kotlin

- Kotlin Gradle Plugin：`1.9.0`。
- KAPT：仅 feature 模块用于 ARouter 编译器。

### 构建变体

- flavor 维度：未配置。
- buildType：默认 `debug`、`release`；`release` 当前不启用代码压缩。
- 默认验证 variant：`debug`。

## 2. 模块依赖关系

### 模块依赖图

```mermaid
graph TD
    app[":app"] --> common[":core:common"]
    app --> navi[":core:navi"]
    app --> permission[":core:permission"]
    bluetooth[":foundation:bluetooth"]
    userFeature --> storage[":core:storage"]
    app --> home[":feature:home"]
    app --> userFeature[":feature:user"]
    app --> questionnaire[":feature:questionnaire"]
    app --> device[":feature:device"]
    app --> collection[":feature:collection"]
    app --> analysis[":feature:analysis"]
    home --> common
    home --> navi
    home --> userProvider
    home --> collectionProvider
    questionnaire --> common
    questionnaire --> navi
    device --> common
    device --> navi
    collection --> common
    collection --> navi
    collection --> collectionProvider
    questionnaire --> collectionProvider
    device --> collectionProvider
    analysis --> common
    analysis --> navi
    userFeature --> common
    userFeature --> navi
    userFeature --> userProvider
```

### 模块依赖表

| 模块 | 类型 | 直接依赖 | 被谁依赖 | 变更影响 |
|---|---|---|---|---|
| `:app` | Android application | common、navi、permission、6 个 feature | 无 | 应用启动、壳层和发布产物 |
| `:core:common` | Android library | AndroidX、Compose | app、全部 feature | 公共 UI 与通用模型的全局影响 |
| `:core:navi` | Android library | Fragment、ARouter API | app、provider、全部 feature | 路由契约、返回策略与导航宿主的全局影响 |
| `:core:permission` | Android library | AndroidX Core、Fragment | app（配置注入） | 可注入权限定义、检查、申请、端内兜底弹窗和设置页跳转的全局影响 |
| `:core:storage` | Android library | AndroidX Core、MMKV | feature:user（及后续需要本地 KV 的模块） | 通用本地键值存储与加密策略 |
| `:foundation:bluetooth` | Android library | AndroidX AppCompat | 暂无 | 经典蓝牙与 BLE 扫描、连接、读写及事件回调基础能力 |
| `:provider:user` | Android library | ARouter API | home、feature:user | 用户资料数据契约的影响面 |
| `:provider:collection` | Android library | navi、ARouter API | home、questionnaire、device、collection | 采集流程状态机契约的影响面 |
| `:feature:home` | Android library | common、navi、provider:user、provider:collection | app | 采集入口与任务列表 |
| `:feature:user` | Android library | common、navi、storage、provider:user | app | 用户资料编辑与本地资料实现 |
| `:feature:questionnaire` | Android library | common、navi、provider:collection | app | 前后问卷路径 |
| `:feature:device` | Android library | common、navi、provider:collection | app | BLE 扫描、连接和设备状态展示；运行时权限由路由守卫处理 |
| `:feature:collection` | Android library | common、navi、provider:collection | app | 采集与记录、流程状态机实现 |
| `:feature:analysis` | Android library | common、navi | app | 分析结果路径 |

## 3. 工程分层

- `:app` 是组合层，持有 `VitalHubApplication`、唯一 `MainActivity`、系统栏/标题栏、底部导航和 Fragment 返回栈。
- `:core:common` 是跨模块基础层，暴露通用模型与共享 Compose 能力；不应依赖业务 feature。
- `:core:navi` 是跨模块导航基础层，暴露 `Routes`、`Navigator`、导航宿主接口、页面元数据、`FlowBackPolicy` 与导航去重；不应依赖业务 feature。
- `:core:permission` 是跨模块基础层，封装可注入的运行时权限契约、检查、申请、端内拒绝兜底弹窗及设置页跳转；应用层配置权限定义、路由守卫和宿主依赖，功能模块仅在各自 Manifest 声明所需权限。
- `:core:storage` 是跨模块基础层，封装本地键值存储；业务模块只依赖其公开的 `KVStorage`/`Storage` API，不直接依赖具体存储后端。
- `:foundation:bluetooth` 是蓝牙基础能力层，封装经典蓝牙与 BLE 的扫描、连接、读写和回调，不依赖业务 feature。
- `:provider:user` 是继承 `IProvider` 的用户资料能力契约层；`:feature:user` 负责资料编辑、MMKV 本地实现及 `/user/service` ARouter 服务注册。
- `:provider:collection` 是继承 `IProvider` 的采集流程状态机契约层；`:feature:collection` 负责 MMKV 实现及 `/collection/flow/service` 服务注册。
- feature 模块不直接依赖彼此，使用 ARouter 路径经 `Navigator` 跳转。

## 4. 跨模块影响面判断

- 修改 `Routes`、`RouteArgs`、`Navigator` 或 `FlowNavigationHost`：检查 app 与所有相关 feature 的路由注册、参数和返回栈。
- 修改 `MainActivity`、`AppShellViewModel`、底栏或标题栏：检查顶级目的地和所有 `AppBarDestination` / `BottomNavigationDestination` 的实现。
- 修改 BLE 权限或 `feature:device` Manifest：同时确认 Android 版本兼容性和应用合并 Manifest。
- 增加共享模型、导航策略或公共 UI：分别优先评估是否属于 `:provider:*`、`:core:navi`、`:core:common`；未被多模块消费的实现留在 feature 内。

## 5. 工程框架文档索引

| 主题 | 场景 | 文档 |
|---|---|---|
| 应用壳与路由 | ARouter、Fragment 宿主、Compose 顶栏与底栏 | `docs/architecture/app-shell.md` |
| 业务架构概览 | 业务模块职责、核心标识、推荐数据层 | `ARCHITECTURE.md` |
