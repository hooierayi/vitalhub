# 采集上传与分析页

## 入口与状态

- 片段采集倒计时完成后，`:feature:collection` 通过 `Navigator.analysis(...)` 打开 `AnalysisActivity`，并传入本次采集的 `sessionId`。
- `AnalysisFragment` 按“上传与分析状态、本次记录信息、关键指标趋势”的顺序展示页面内容。
- 页面标题沿用“AI分析结果”。
- 本次记录信息不使用演示值：采集日期读取当前 `sessionId` 在 `CollectionFlowProvider` 中保存的首次采集完成时间，采集设备展示 `DeviceProvider` 中最近成功连接设备的 MAC 地址，采集人读取 `UserInfoProvider` 中的当前用户；真实数据缺失时显示“-”。

## Mock 上传与分析流程

- 页面进入后立即进入“采集数据上传中”，mock 进度从 0% 递增到 100%。
- 上传完成后进入“上传完成，分析中”，模拟等待服务器返回分析结果。
- 上传和分析期间，底部“回首页”与“填写采集后问卷”均置灰且不可点击。
- 服务端结果返回后进入“上传与分析完成”，展示关键指标趋势并开放“回首页”与“填写采集后问卷”。
- 状态模型同时保留失败态，失败时左侧“回首页”保持置灰，右侧主按钮切换为可用的“重新上传”；当前 mock 默认走成功链路。

## 底部操作

- “回首页”是左侧低强调操作，首页图标位于文字上方；点击后结束 `AnalysisActivity`，恢复返回栈中的首页。
- “填写采集后问卷”是右侧主操作；通过 `Navigator.flow(...)` 和 `FlowDestination.POST_QUESTIONNAIRE` 打开后问卷，并继续传递当前 `sessionId`。
- 后问卷到达后移除 `AnalysisActivity`，避免提交或返回时重新落到上传页；后问卷提交完成后恢复首页。

## 返回策略

- 上传中、分析中和失败态会统一拦截标题栏返回与系统返回，避免流程中途退出；分析完成后才恢复返回能力。
- 分析完成后的标题栏返回与系统返回由 `AnalysisActivity.onRootBackPressed()` 处理，行为与“回首页”一致。
- 路由失败或被拦截时保留当前页面，不提前结束 `AnalysisActivity`。
