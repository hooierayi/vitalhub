# 采集上传与分析页

## 入口与状态

- 片段采集倒计时完成后，`:feature:collection` 通过 `Navigator.analysis(...)` 打开 `AnalysisActivity`，只传入本次采集的唯一 `recordId`。
- `AnalysisFragment` 按“上传与分析状态、本次记录信息、关键指标趋势”的顺序展示页面内容。
- 页面标题沿用“AI分析结果”。
- 本次记录信息不使用当前流程或设备的瞬态值：采集日期和设备地址直接读取 `recordId` 对应的 Record，采集人根据 Record 保存的用户指纹查询历史用户资料；真实数据缺失时显示“-”。

## 上传与分析流程

- 页面通过 `RecordProvider.getRecordById(recordId)` 精确获取完整记录、本地 `.dcm` 路径和 `analysisId`；即使同一个 `sessionId` 下发生多次采集，也不会读取到另一条记录。页面展示的记录编号、完成时间、设备地址、采集人，以及后问卷和重新采集所需的 `sessionId`，均从这条 Record 恢复。
- `:feature:analysis` 使用 `:core:network` 创建固定 Retrofit/OkHttp 客户端；Release base URL 由 `analysisBaseUrl` 注入，默认为 `https://app.friendshipoffice.xyz/`；Debug base URL 由 `analysisDebugBaseUrl` 注入，默认为 `http://47.98.175.38:8000/`。API Key 和 App 版本分别由 `analysisApiKey` 与 `versionName` Gradle 属性注入。API Key 缺失时不发起匿名请求，而是进入不可重试的服务配置失败态。
- 上传严格按接口约定发送 `data`、`app_version`、`protocol_version` 三个 multipart 字段，文件媒体类型为 `application/dicom`，认证请求头为 `X-API-Key`。
- Retrofit 保留原始 HTTP 状态：先按 `2xx`、`4xx`、`5xx` 判断请求结果大类，再使用统一 `AnalysisBusinessCode` 细分业务语义。上传与查询共用业务码定义，但分别映射为各自的领域结果；非 `2xx` 不因响应体中的业务码被误判为成功。
- 上传的 `HTTP 202 + code 100/102` 和 `HTTP 200 + code 101` 均可接收；只要响应包含合法 `analysis_id` 和已知异步状态，就持久化任务并进入查询。已知业务码之外的 `2xx` 仅在 `analysis_id` 与 `status` 均合法时兼容接收，避免服务端已经接收后重复上传。
- 上传和查询成功响应中的 `poll_interval_secs` 表示下一次 GET 前等待的秒数；值大于 0 时优先使用，字段缺失、为 0 或为负数时使用 `AnalysisConfig.POLL_FALLBACK_INTERVAL_SECONDS`，当前配置为 10 秒。已有 `analysisId` 的页面重新进入时会先立即 GET 获取最新状态和服务端间隔。最多查询 200 次；`queued`、`processing`、`retrying` 继续轮询。查询遇到网络错误或 `503/5101` 时使用端内兜底间隔自动重试，连续三次后进入可“继续查询”的失败态。
- 采集记录 Room v1 数据库只保存可空的 `analysisId`。页面进入时，`analysisId` 为空则 POST 上传，非空则直接 GET 查询；远端状态、HTTP/业务错误、恢复动作、Markdown 结果均由页面状态机管理，不持久化。重新进入已完成任务也会联网查询最新结果，网络失败按普通查询错误显示“继续查询”。
- 页面状态机以 `Uploading`、`Waiting`、`Completed`、`Failed` 四类封闭状态驱动 UI，其中 `Waiting` 再区分 `QUEUED`、`PROCESSING`、`RETRYING`。失败动作收敛为重新上传、继续查询、重新分析、重新采集和不可重试；不存在旧状态与按钮组合不一致的中间状态。
- 服务端当前只返回 Markdown 结果，因此页面移除 Mock 指标卡，并使用 Markwon 在原报告卡片内渲染报告内容；空结果仍显示占位文案。渲染器启用 CommonMark、GFM 表格/删除线/任务列表、HTML、Markdown 图片（包含常规位图、GIF、SVG）、块级与行内 LaTeX、代码语法高亮、URL/邮箱/电话自动链接和软换行。代码高亮生成器包含 Prism4j 提供的全部语言定义；表格内链接使用专用触摸处理。
- 文件上传期间，右侧主按钮显示“上传中”与 loading，底部操作和系统返回均不可用，避免服务端是否接收成功尚未明确时取消请求并造成重复上传。
- 服务端返回 `analysis_id` 后，即使任务仍处于排队、分析或服务端重试状态，也立即开放“回首页”和“填写采集后问卷”；重新进入分析页时使用已保存的 `analysis_id` 查询远端状态。失败时根据状态机动作显示“重新上传”“继续查询”“重新分析”或“重新采集”，不可恢复的参数、认证和冲突错误不提供无效重试。

## 上传接口处理码表

接口：`POST /api/v1/analyze`

| HTTP | 业务码 | 服务端状态 | 页面状态 | 失败动作 | App 处理 |
|---:|---:|---|---|---|---|
| 202 | 100 | `processing` | `Waiting(PROCESSING)` | - | 保存 `analysisId`，按服务端间隔或端内兜底等待后 GET |
| 202 | 102 | `queued` | `Waiting(QUEUED)` | - | 保存 `analysisId`，按服务端间隔或端内兜底等待后 GET |
| 200 | 101 | `already_received` | 响应中的等待状态；缺失时为 `Waiting(PROCESSING)` | - | 不重复上传，保存已有 `analysisId`，等待后 GET |
| 2xx | 103 | `retrying` | `Waiting(RETRYING)` | - | 保存 `analysisId`，等待后 GET |
| 2xx | 未知 | 合法的 `queued`、`processing` 或 `retrying` | 对应 `Waiting` 状态 | - | 兼容服务端新增业务码，保存 `analysisId`，等待后 GET |
| 2xx | 任意 | 缺少有效 `analysisId` | `Failed` | `RETRY_UPLOAD` | 不保存任务编号，显示“重新上传” |
| 2xx | 已知但不适用于上传 | 任意 | `Failed` | `RETRY_UPLOAD` | 按上传协议异常处理 |
| 400 | 1001 | `invalid_parameter` | `Failed` | `NONE` | 参数错误，不重试 |
| 400 | 1002 | `unsupported_protocol` | `Failed` | `RECOLLECT_DATA` | 显示“重新采集” |
| 400 | 1003 | `invalid_data_format` | `Failed` | `RECOLLECT_DATA` | 显示“重新采集” |
| 400 | 1004 | `checksum_failed` | `Failed` | `RETRY_UPLOAD` | 显示“重新上传” |
| 401 | 1101 | `unauthorized` | `Failed` | `NONE` | 认证失败，不重试 |
| 409 | 1301 | `session_conflict` | `Failed` | `NONE` | 不覆盖服务端任务 |
| 503 | 5101 | `service_unavailable` | `Failed` | `RETRY_UPLOAD` | 显示“重新上传” |
| 网络错误 | - | - | `Failed` | `RETRY_UPLOAD` | 显示“重新上传” |
| 响应无法解析 | - | - | `Failed` | `RETRY_UPLOAD` | 显示“重新上传” |
| 其他非 2xx | 任意 | - | `Failed` | `NONE` | 按 HTTP 错误停止处理 |

处理顺序固定为先判断 HTTP 状态，再使用业务码细分动作；非 2xx 响应不会因为错误体包含成功业务码而转成成功。

成功响应的轮询间隔规则：`poll_interval_secs > 0` 时换算为毫秒使用，否则使用端内配置，当前为 10 秒。

## 查询接口处理码表

接口：`GET /api/v1/result/{analysisId}`

| HTTP | 业务码 | 服务端状态 | 页面状态 | 失败动作 | App 处理 |
|---:|---:|---|---|---|---|
| 200 | 0 | `completed` | `Completed(markdown)` | - | 展示本次返回的 Markdown，停止查询 |
| 200 | 100 | `processing` | `Waiting(PROCESSING)` | - | 按 `poll_interval_secs` 或端内兜底间隔继续 GET |
| 200 | 102 | `queued` | `Waiting(QUEUED)` | - | 按 `poll_interval_secs` 或端内兜底间隔继续 GET |
| 200 | 103 | `retrying` | `Waiting(RETRYING)` | - | 按 `poll_interval_secs` 或端内兜底间隔继续 GET |
| 404 | 1201 | `not_found` | `Failed` | `RESTART_ANALYSIS` | 显示“重新分析”；点击后清空旧 `analysisId` 并 POST |
| 401 | 1101 | `unauthorized` | `Failed` | `NONE` | 停止查询，不重试 |
| 403 | 1102 | `forbidden` | `Failed` | `NONE` | 停止查询，不重试 |
| 500 | 5003 | `failed` | `Failed` | `RESTART_ANALYSIS` | 显示“重新分析” |
| 504 | 5102 | `timeout` | `Failed` | `RESTART_ANALYSIS` | 显示“重新分析” |
| 503 | 5101 | `service_unavailable` | 前两次 `Waiting(RETRYING)` | `RESUME_QUERY` | 保留 `analysisId`，按端内兜底 10 秒自动 GET |
| 连续第 3 次 503 | 5101 | `service_unavailable` | `Failed` | `RESUME_QUERY` | 停止自动查询，显示“继续查询” |
| 网络错误 | - | - | 前两次 `Waiting(RETRYING)` | `RESUME_QUERY` | 保留 `analysisId`，按端内兜底 10 秒自动 GET |
| 连续第 3 次网络错误 | - | - | `Failed` | `RESUME_QUERY` | 停止自动查询，显示“继续查询” |
| 200 | 任意 | 返回的 `analysisId` 与请求不一致 | `Failed` | `NONE` | 防止串任务，停止查询 |
| 200 | 任意 | 业务码与 `status` 冲突或无法识别 | `Failed` | `NONE` | 按查询协议异常处理 |
| 其他非 2xx | 任意 | - | `Failed` | `NONE` | 按 HTTP 错误停止查询 |
| 达到 200 次查询上限 | - | 未完成 | `Failed` | `RESUME_QUERY` | 保留 `analysisId`，显示“继续查询” |

没有网络时不增加独立离线状态：POST 失败进入 `RETRY_UPLOAD`，GET 失败保留 `analysisId` 并按查询临时错误处理。

## 页面状态机与按钮

状态机只有四类封闭状态，`Waiting` 的三种子状态形成六种用户可见页面状态：

| 页面状态 | 页面展示 | 是否自动请求 | 回首页 | 右侧主按钮 |
|---|---|---|---|---|
| `Uploading(progress)` | 上传进度 | 等待 POST | 禁用 | “上传中”，禁用并显示 loading |
| `Waiting(QUEUED)` | 排队中 | 服务端间隔优先，否则 10 秒后 GET | 可用 | “填写采集后问卷” |
| `Waiting(PROCESSING)` | 分析中 | 服务端间隔优先，否则 10 秒后 GET | 可用 | “填写采集后问卷” |
| `Waiting(RETRYING)` | 服务端重试或临时查询失败 | 服务端间隔优先；请求失败时 10 秒后 GET | 可用 | “填写采集后问卷” |
| `Completed(markdown)` | 分析报告 | 停止 | 可用 | “填写采集后问卷” |
| `Failed(message, action)` | 错误原因 | 等待用户操作 | 可用 | 由 `action` 决定 |

失败动作与按钮功能：

| `AnalysisFailureAction` | 按钮文案 | 点击行为 | `analysisId` 处理 |
|---|---|---|---|
| `RETRY_UPLOAD` | 重新上传 | 再次 POST | 上传前确保为空 |
| `RESUME_QUERY` | 继续查询 | 使用原任务执行 GET | 保留 |
| `RESTART_ANALYSIS` | 重新分析 | 清除旧任务后执行 POST | 先清空，成功后保存新值 |
| `RECOLLECT_DATA` | 重新采集 | 返回片段采集页 | 不作为后续任务使用 |
| `NONE` | 服务异常（红色） | 按钮禁用，不响应点击 | 不修改 |

## Record 持久化字段

分析流程只在 `collection_records` 保存一个分析字段：

| 字段 | 类型 | 可空 | 含义 |
|---|---|---:|---|
| `analysisId` | `TEXT` | 是 | 服务端分析任务编号；为空执行 POST，非空直接执行 GET |

远端状态、上传进度、HTTP/业务错误码、错误信息、失败动作和 Markdown 结果均为当前页面状态，不写入数据库。Room 数据库版本固定为 1，不提供历史 schema 迁移；安装过其他版本数据库的调试设备需要清除 App 数据或卸载重装。

## 底部操作

- “回首页”是左侧低强调操作，首页图标位于文字上方；点击后结束 `AnalysisActivity`，恢复返回栈中的首页。
- “填写采集后问卷”是右侧主操作；通过 `Navigator.flow(...)` 和 `FlowDestination.POST_QUESTIONNAIRE` 打开后问卷，并继续传递当前 `sessionId`。
- 分析页使用独立的 `analysisEntryMode` 区分来源，不复用控制流程返回方式的 `flowEntryMode`。采集完成进入时使用 `FROM_COLLECTION`，页面首屏即固定展示左侧“回首页”和右侧“填写采集后问卷”；上传完成并取得 `analysisId` 前按钮保持禁用，服务端受理后启用。首页记录卡片进入时使用 `FROM_RECORD`，上传成功受理、排队、分析中和完成态不显示后问卷入口，底部改为全宽“回首页”，上传阶段显示全宽禁用的“上传中”。失败态仍显示左侧“回首页”和右侧恢复操作。
- 后问卷到达后移除 `AnalysisActivity`，避免提交或返回时重新落到上传页；后问卷提交完成后恢复首页。

## 返回策略

- 仅文件上传期间拦截标题栏返回与系统返回；服务端确认任务或进入失败态后恢复返回能力。
- 恢复后的标题栏返回与系统返回由 `AnalysisActivity.onRootBackPressed()` 处理，行为与“回首页”一致。
- 路由失败或被拦截时保留当前页面，不提前结束 `AnalysisActivity`。

## 待服务端确认

- `already_received` 必须返回可查询的 `analysis_id`，否则 App 无法按建议直接查询既有任务。
- DICOM SOP Class、Private Creator、Modality、ECG Channel Label、Patient ID Issuer、设备序列号、IMU 单位与湿度符号类型仍以双方最终确认版本为准，当前网络接入不修改文件协议实现。
