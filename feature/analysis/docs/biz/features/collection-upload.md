# 采集上传与分析页

## 入口与状态

- 片段采集倒计时完成后，`:feature:collection` 通过 `Navigator.analysis(...)` 打开 `AnalysisActivity`，并传入本次采集的 `sessionId`。
- `AnalysisFragment` 按“上传与分析状态、本次记录信息、关键指标趋势”的顺序展示页面内容。
- 页面标题沿用“AI分析结果”。
- 本次记录信息不使用演示值：采集日期读取当前 `sessionId` 在 `CollectionFlowProvider` 中保存的首次采集完成时间，采集设备展示 `DeviceProvider` 中最近成功连接设备的 MAC 地址，采集人读取 `UserInfoProvider` 中的当前用户；真实数据缺失时显示“-”。

## 上传与分析流程

- 页面通过 `RecordProvider.getRecordBySessionId(sessionId)` 获取已完成片段的本地 `.dcm` 路径，不通过路由参数转发可恢复数据。
- `:feature:analysis` 使用 `:core:network` 创建固定 Retrofit/OkHttp 客户端；Release base URL 由 `analysisBaseUrl` 注入，默认为 `https://app.friendshipoffice.xyz/`；Debug base URL 由 `analysisDebugBaseUrl` 注入，默认为 `http://47.98.175.38:8000/`。API Key 和 App 版本分别由 `analysisApiKey` 与 `versionName` Gradle 属性注入。API Key 缺失时不发起匿名请求，而是进入可重试失败态。
- 上传严格按讨论稿发送 `data`、`app_version`、`protocol_version` 三个 multipart 字段，文件媒体类型为 `application/dicom`，认证请求头为 `X-API-Key`。服务端未正式确认的 `session_id` 和 checksum 不擅自加入请求。
- 上传成功后持久化 `analysis_id`，每 3 秒查询一次结果，最多查询 200 次；`queued`、`processing`、`retrying` 继续轮询，连续三次网络或服务不可用错误后进入失败态。
- 采集记录 Room 数据库保存最新分析状态、`analysis_id`、Markdown 结果与错误信息。页面重新进入时，已完成任务直接恢复结果；排队或处理中任务从已保存的 `analysis_id` 继续查询，不重复上传。
- 服务端当前只返回 Markdown 结果，因此页面移除 Mock 指标卡，以纯文本方式安全展示原始报告内容。
- 文件上传期间，右侧主按钮显示“上传中”与 loading，底部操作和系统返回均不可用，避免服务端是否接收成功尚未明确时取消请求并造成重复上传。
- 服务端返回 `analysis_id` 后，即使任务仍处于排队、分析或服务端重试状态，也立即开放“回首页”和“填写采集后问卷”；重新进入分析页时使用已保存的 `analysis_id` 恢复轮询。失败时开放“回首页”，主按钮切换为“重新上传”。

## 底部操作

- “回首页”是左侧低强调操作，首页图标位于文字上方；点击后结束 `AnalysisActivity`，恢复返回栈中的首页。
- “填写采集后问卷”是右侧主操作；通过 `Navigator.flow(...)` 和 `FlowDestination.POST_QUESTIONNAIRE` 打开后问卷，并继续传递当前 `sessionId`。
- 后问卷到达后移除 `AnalysisActivity`，避免提交或返回时重新落到上传页；后问卷提交完成后恢复首页。

## 返回策略

- 仅文件上传期间拦截标题栏返回与系统返回；服务端确认任务或进入失败态后恢复返回能力。
- 恢复后的标题栏返回与系统返回由 `AnalysisActivity.onRootBackPressed()` 处理，行为与“回首页”一致。
- 路由失败或被拦截时保留当前页面，不提前结束 `AnalysisActivity`。

## 待服务端确认

- `already_received` 必须返回可查询的 `analysis_id`，否则 App 无法按建议直接查询既有任务。
- 上传请求尚未定义显式 `session_id` 与 checksum 字段，当前客户端不添加未约定字段；服务端若依赖二者实现幂等和完整性校验，需要先更新接口契约。
- DICOM SOP Class、Private Creator、Modality、ECG Channel Label、Patient ID Issuer、设备序列号、IMU 单位与湿度符号类型仍以双方最终确认版本为准，当前网络接入不修改文件协议实现。
