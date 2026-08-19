# VitalHub 工程架构

## 模块

```text
app
 └─ MainActivity（唯一 Activity / Fragment 宿主）
     ├─ core:navi
     ├─ feature:home ────────► provider:user
     │                    └─► provider:collection
     ├─ feature:user ────────► provider:user
     ├─ feature:questionnaire ─► provider:collection
     ├─ feature:device ─────────► provider:collection
     ├─ feature:collection ─────► provider:collection
     └─ feature:analysis
core:navi ◄──────────── app、provider 与所有 feature
feature:user ──────────► core:storage
```

- `app`：应用壳、唯一 `MainActivity`、Compose 底部导航、Fragment 返回栈和 ARouter 初始化。
- `core:common`：跨模块通用模型、Compose 主题与公共组件；不依赖任何业务模块。
- `core:navi`：ARouter 路径与参数、`Navigator`、Fragment 导航宿主契约、流程返回策略与导航去重；不依赖业务 feature。
- `core:storage`：可复用的本地键值存储，提供 SharedPreferences/MMKV 后端、批量编辑和可选 Android Keystore 加密。
- `provider:user`：用户资料模型和 ARouter Provider 契约。
- `provider:collection`：首页采集流程检查点、事件与 ARouter Provider 契约。
- `feature:home`：采集任务入口与任务列表。
- `feature:user`：用户信息编辑页面，以及以 ARouter Provider 暴露的 MMKV `UserInfoProvider` 实现。
- `feature:questionnaire`：采集前睡眠问卷、采集后热相关症状问卷。
- `feature:device`：BLE 权限、扫描、连接及设备状态展示。
- `feature:collection`：实时预览、2 分钟片段、本地缓存/上传、连续记录，以及采集流程状态机的 MMKV Provider 实现。
- `feature:analysis`：异步 AI 分析任务及结果。

业务模块之间不得添加直接 Gradle 依赖。每个模块用 `@Route` 暴露 Fragment，
跨模块跳转统一经过 `core:navi` 中的 `Routes`、`RouteArgs` 与 `Navigator`。
ARouter 负责发现和创建目标 Fragment，`MainActivity` 实现的 `FlowNavigationHost`
负责 FragmentTransaction、动画和返回栈。

## 底部导航

底部导航属于应用级 UI，由 `app` 模块统一持有，包含“采集、记录、报告、我的”
四个顶级入口。顶级 Tab 切换不加入业务返回栈；进入问卷、设备连接、采集和分析
等子流程时隐藏底栏，返回实现 `BottomNavigationDestination` 的顶级 Fragment 时自动恢复。
记录、报告和我的目前由 app 内占位页面承载，业务成熟后可迁移到独立 feature 模块，
底栏逻辑无需变化。

## 沉浸式标题栏

状态栏与标题栏同样属于 `app` 壳层。`MainActivity` 使用 edge-to-edge 窗口，透明
状态栏和浅色系统图标，并由 Compose `AppTitleBar` 统一处理 `statusBarsPadding`。
各业务 Fragment 通过 `AppBarDestination` 声明标题和是否显示返回按钮，业务页面
内容不再重复绘制标题。系统返回和标题栏返回按钮共用 Activity 返回栈逻辑。

所有业务页面内容使用 Jetpack Compose。Fragment 只作为 ARouter 路由入口和生命周期
容器，通过 `ComposeView` 承载对应模块的 Composable；不再维护 XML 页面布局或 ViewBinding。

## 页面复用

- `QuestionnaireFragment` 通过 `questionnairePhase=pre/post` 展示前问卷或后问卷；
- `CollectionFragment` 通过 `collectionMode=preview/clip/continuous` 展示实时预览、
  2 分钟片段采集或连续记录；
- Activity 级状态后续放入共享 ViewModel，BLE 长连接放入前台 Service，不能依赖
  Fragment 生命周期。

## 核心标识

- `sessionId`：一次用户采集任务，关联前后问卷、设备、片段、上传与诊断结果。
- `recordId`：记录仪创建的连续记录文件标识。
- `cmdSeq`：设备控制命令序号，用于 ACK/NACK、超时重试和幂等处理。

首页采集流程固定为“采集前问卷、连接设备、开始采集、采集后问卷”四步。前问卷、
设备连接、正常结束采集和后问卷提交依次使进度达到 1/4 至 4/4。Provider 以事件 reducer
处理检查点，重复事件保持当前或更后的进度，避免用户在上一级页面重新进入流程时被阻塞。
设备连接与正在采集属于瞬态状态，应用恢复时回退到重新连接设备。实时预览是
采集阶段的主页面；片段采集或连续记录使用系统返回或标题栏返回时都回到实时预览。采集后
问卷使用系统返回或标题栏返回时回到首页。AI 分析不属于首页流程，后问卷提交后直接返回首页。

## 推荐的数据层边界

后续实现时，在对应 feature 内采用 `ui / domain / data` 三层：

- `device` 对外提供连接状态和原始数据流，不向 UI 暴露 BLE GATT 细节；
- `collection` 负责分片重组、CRC、文件缓存、断点上传和采集状态机；
- `questionnaire` 以配置模型渲染题目，答案仅通过 `sessionId` 关联；
- `analysis` 只消费 `analysisTaskId` 的异步状态。

协议解析若会被多个业务模块消费，再下沉为独立的 `core:protocol`，不要提前把
尚未定稿的设备协议固化到公共模块。
