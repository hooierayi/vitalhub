# 采集上传记录

## 1. 定义

- 定义：一次正常完成的片段采集文件的本地持久化与上传摘要。
- 每条记录以 App 本地 `id` 唯一标识，并保存 `sessionId`、采集类型、记录时间、时长、本地文件路径、`userFingerprint`、`deviceAddress` 和可空的服务端 `analysisId`。`analysisId` 为空时分析页上传文件，非空时直接查询服务端；分析状态、HTTP/业务错误、恢复动作和 Markdown 结果均为页面瞬态，不写入记录库。片段记录编号使用 `CLIP-` 前缀，后接 UUID 去连字符后的前 12 位十六进制字符；历史 `REC-` 编号保持不变。
- 只有片段采集完成才写入；实时预览、暂停、中途退出和设备内部连续写卡均不构成采集上传记录。

## 2. 所属边界

- `:provider:record` 定义 `CollectionRecord` 与 `RecordProvider` 契约。
- `:feature:collection` 使用 Room 保存记录与服务端分析任务编号，并以 `/record/service` 注册 ARouter Provider；`:feature:analysis` 只经该 Provider 查询记录、保存或清空 `analysisId`。
- `:app` 的底栏记录页通过 Provider 观察全部记录，按记录时间倒序展示；点击卡片只使用唯一 `recordId` 直达分析页，由分析页通过 Provider 精确读取完整记录。该入口标记为 `FROM_RECORD`，不展示后问卷操作；Record 中的 `sessionId` 仍可用于错误恢复时重新采集。用户名称通过 Record 的 `userFingerprint` 和 `UserInfoProvider` 解析，设备直接展示 Record 中的 `deviceAddress`。
- 记录库不创建 `devices` 表；`deviceAddress` 直接保存并展示采集时的 MAC 地址。用户表归属 `:feature:user` 的独立 Room 数据库，因此 `userFingerprint` 是跨库逻辑关联，不声明 SQLite 外键。

## 3. 易混淆概念

| 概念 | 区别 |
|---|---|
| 采集会话 | `sessionId` 关联前后问卷和一次完整任务；同一会话可产生多条记录，不能用它唯一选择分析记录。 |
| 连续记录 | 表示设备内部写卡操作，存放在 `DeviceInfo.record`，不写入采集上传记录表。 |
