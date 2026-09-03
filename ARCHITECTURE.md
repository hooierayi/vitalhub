# VitalHub 工程架构

## 模块

```text
app
 └─ MainActivity（首页 Fragment 宿主）
     ├─ core:navi
     ├─ core:permission
     ├─ feature:home ────────► provider:user
     │                    └─► provider:collection
     ├─ feature:user（Activity + Fragment）────► provider:user
     ├─ feature:questionnaire（Activity + Fragment）► provider:collection
     ├─ feature:collection（Activity + device/collection Fragments）► provider:collection / provider:record
     └─ feature:analysis（Activity + Fragment）
core:navi ◄──────────── app、provider 与所有 feature
```

- `app`：应用首页壳、`MainActivity`、Compose 底部导航、首页 Fragment 返回栈和 ARouter 初始化。
- `core:common`：跨模块通用模型、Compose 主题与公共组件；不依赖任何业务模块。
- `foundation:device-waveform-ui`：不依赖设备协议的实时波形 UI，按物理图纸标定绘制网格、ECG 和呼吸，提供固定容量采样环形缓冲、扫屏与滚动模式。
- `core:navi`：ARouter 路径与参数、`Navigator`、Fragment 导航宿主契约、流程返回策略与导航去重；不依赖业务 feature。
- `core:permission`：可注入的运行时权限定义、统一检查/申请、端内拒绝兜底弹窗及应用设置页跳转；不依赖业务 feature。应用层注入权限、路由守卫、前台 Activity 来源和弹窗实现，具体权限声明由使用它的 feature Manifest 提供。
- `core:storage`：可复用的本地键值存储，提供 SharedPreferences/MMKV 后端、批量编辑和可选 Android Keystore 加密。
- `core:network`：可复用的 Retrofit/OkHttp 网络客户端，提供 Gson 转换、超时、动态请求头和显式启用的脱敏 HTTP 日志配置；base URL 与业务接口归属各 feature。
- `foundation:bluetooth`：可复用的经典蓝牙与 BLE 基础组件，提供扫描、连接、读写及事件回调能力。
- `foundation:device-api`：业务可见的 `DeviceSdk`、`DeviceSession`、聚合数据帧、指令与状态契约。
- `foundation:device-transport`：把底层蓝牙回调适配为有序字节流和挂起式写入，不识别协议。
- `foundation:device-protocol`：自动扩容环形缓冲、可配置拦截器管线、拆包、校验、序号连续性和聚合帧解析。
- `foundation:device-command`：有界优先级指令队列、单飞 Worker、编码、回执匹配、超时及幂等重试。
- `foundation:device-storage`：解析后聚合帧的异步无静默丢帧存储和 `.part` 文件收口。
- `foundation:file-protocol`：基于 `dcm4che-core` 的可交换文件协议基础能力，持续接收采集值并在停止或容量滚动时生成 DICOM `.dcm`；不自研 DICOM 二进制编码，也不引入图像编解码或网络栈。片段采集由 `feature:collection` 通过可靠 `RecorderFrameSink` 接入，UI `frames` 流不用于文件落盘。
- `foundation:device-waveform`：从聚合帧分别投影 `EcgWaveformFrame` / `RespirationWaveformFrame`，并隔离慢 UI 消费者。
- `foundation:device-sdk`：以上能力的总壳和会话编排；业务不负责组装及转发字节流。
- `provider:user`：用户资料模型和 ARouter Provider 契约。
- `provider:collection`：首页采集流程检查点、事件与 ARouter Provider 契约。
- `provider:record`：已完成采集记录及服务端分析任务编号模型，以及查询、保存的 ARouter Provider 契约。
- `feature:home`：采集任务入口与任务列表。
- `feature:user`：用户信息编辑页面，以及以 ARouter Provider 暴露的 Room `UserInfoProvider` 实现；用户以姓名、性别、年龄的 SHA-256 指纹关联，修改资料时切换 active/inactive。
- `feature:questionnaire`：采集前睡眠问卷、采集后热相关症状问卷。
- `feature:collection`：采集流程 Activity，内部包含 BLE 扫描、连接、设备状态、实时预览、2 分钟片段、本地缓存/上传、连续记录，以及采集流程状态机的 MMKV Provider 和完成记录的 Room Provider 实现；运行时权限在进入 Activity 前处理。
- `feature:analysis`：上传本地 DICOM、基于持久化 `analysisId` 轮询异步 AI 分析任务，并以页面状态机展示进度、错误和 Markdown 结果；报告内 PDF 链接由本模块在应用内下载并分页展示。
- `debug:dokit-bluetooth`、`debug:dokit-protocol`、`debug:dokit-waveform`：仅通过
  `app` 的 `debugImplementation` 接入的 DoKit 自定义工具，分别以可拖动的 App 内悬浮卡片
  观察蓝牙原始收发、协议/指令交互和波形环形缓冲，并可进入完整详情页；不申请系统悬浮窗权限，
  release 变体不打包 DoKit。

业务模块之间不得添加直接 Gradle 依赖。首页用 `@Route` 暴露 Fragment，其他业务模块
用 `@Route` 暴露 Activity 外部入口和 Fragment 内部入口。跨模块跳转统一经过 `core:navi`
中的 `Routes`、`RouteArgs` 与 `Navigator`。ARouter 负责发现 Activity 和解析 Fragment，
`MainActivity` 或目标业务 Activity 实现的 `FlowNavigationHost` 负责 FragmentTransaction、动画和返回栈。

## 底部导航

底部导航属于应用级 UI，由 `app` 模块统一持有，包含“采集、记录、报告、我的”
四个顶级入口。顶级 Tab 切换不加入业务返回栈；进入问卷、设备连接、采集和分析
等子流程时隐藏底栏，返回实现 `BottomNavigationDestination` 的顶级 Fragment 时自动恢复。
记录页由 app 壳通过 `:provider:record` 查询本地完成记录；报告和我的目前由 app 内占位页面承载，业务成熟后可迁移到独立 feature 模块，
底栏逻辑无需变化。

## 沉浸式标题栏

`MainActivity` 与业务 Activity 均使用 edge-to-edge 窗口、透明状态栏和浅色系统图标，
并复用 Compose 标题栏。各业务 Fragment 通过 `AppBarDestination` 声明标题和是否显示
返回按钮，系统返回和标题栏返回按钮共用所属 Activity 的返回栈逻辑。

所有业务页面内容使用 Jetpack Compose。Activity 作为非首页 feature 的跨模块入口与
Fragment 宿主，Fragment 作为内部 ARouter 路由入口和生命周期容器，通过 `ComposeView`
承载对应模块的 Composable；不维护 XML 页面布局或 ViewBinding。

## 页面复用

- `QuestionnaireFragment` 通过 `questionnairePhase=pre/post` 展示前问卷或后问卷；
- `CollectionFragment` 通过 `collectionMode=preview/clip/continuous` 展示实时预览、
  2 分钟片段采集或连续记录；
- Activity 级状态后续放入共享 ViewModel，BLE 长连接放入前台 Service，不能依赖
  Fragment 生命周期。

## 核心标识

- `sessionId`：一次用户采集任务，关联前后问卷、设备、片段、上传与诊断结果。
- `DeviceRecordInfo.id`：App 为一次设备内部连续写卡操作生成的本地跟踪标识；设备协议回执当前不提供设备侧文件编号。
- 采集上传记录：片段采集倒计时结束后，由 `feature:collection` 将本地文件摘要写入 Room；记录仅以 `userFingerprint` 和 `deviceAddress` 逻辑关联 Provider 数据，不创建设备表。`app` 的记录页通过 `RecordProvider` 观察全部记录并按记录时间倒序展示。
- 设备写卡记录：连续记录启停状态保存在 `DeviceProvider` 的 `DeviceInfo.record` 中，不写入采集上传记录表。
- 当前设备协议没有请求 ID：控制指令通过有界优先级队列严格单飞，以响应码匹配回执；只有明确幂等的命令允许超时重试。

首页采集流程固定为“采集前问卷、数据采集、采集后问卷”三步。前问卷、正常结束采集和
后问卷提交依次使进度达到 1/3 至 3/3。Provider 以事件 reducer 处理检查点，重复事件保持
当前或更后的进度，避免用户在上一级页面重新进入流程时被阻塞。设备连接与正在采集属于
采集 Activity 内的瞬态状态，不进入持久化检查点；每次从首页进入数据采集均从连接页开始。实时预览是
采集阶段的主页面；片段采集或连续记录使用系统返回或标题栏返回时都回到实时预览。采集后
问卷使用系统返回或标题栏返回时回到首页。连续记录启动成功后可选择重发采集指令返回实时
预览，或仅断开蓝牙并清空采集子页面后返回连接记录仪页面。片段采集完成后进入采集上传与分析页，用户可直接
回首页，也可携带当前 `sessionId` 继续填写采集后问卷；进入后问卷后，上传与分析页从返回栈移除。
首页卡片可直达对应业务且完成后返回首页；卡片完成态独立记录业务是否完成。底部按钮只作为
从前问卷开始的顺序流程入口，无个人信息时优先进入资料填写。AI 分析不属于首页流程，后问卷提交后直接返回首页。

## 数据层边界

后续实现时，在对应 feature 内采用 `ui / domain / data` 三层：

- `collection` 只依赖 `DeviceSession` 连接状态、命令和类型化数据流，不向 UI 暴露 BLE GATT 或协议细节；
- 字节流重组、校验、聚合帧解析、波形投影和本地记录由 `foundation:device-*` 模块实现；详细边界见 `docs/architecture/device-sdk.md`；
- `questionnaire` 以配置模型渲染题目，答案仅通过 `sessionId` 关联；
- `analysis` 只消费 `analysisTaskId` 的异步状态。

设备相关实现按职责位于 `foundation:device-*`；尚未确认的 UUID、标量字节序和校验范围必须由设备配置注入，不得作为既有事实固化。
