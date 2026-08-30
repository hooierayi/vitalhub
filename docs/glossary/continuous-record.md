# 连续记录

## 1. 定义

- 定义：记录仪内部一次写卡操作的启动记录，由 `DeviceInfo.record` 中的 `DeviceRecordInfo` 表示。
- 作用范围：连续记录采集模式。
- `DeviceRecordInfo.id` 由 App 生成，用于本地跟踪该次操作；`startedAtEpochMillis` 保存启动成功时间。当前协议不保存结束时间，设备协议回执也不提供设备侧文件编号。
- 常见代码关键词：`DeviceInfo.record`、`DeviceRecordInfo`、`CollectionMode.CONTINUOUS`。

## 2. 相关位置

| 类型 | 路径/模块 | 说明 |
|---|---|---|
| 采集模式 | `:core:navi` | `CollectionMode` 定义连续采集模式 |
| 数据契约 | `:provider:device` | `DeviceInfo` 内嵌可选的 `DeviceRecordInfo` |
| 业务边界 | `:feature:collection` | 负责连续记录场景 |
| 架构证据 | `ARCHITECTURE.md` | 核心标识章节 |

## 3. 相关功能文档

| 功能 | 文档 | 使用场景 |
|---|---|---|
| 连续记录 | `feature/collection/docs/biz/features/collection-flow.md` | 启停设备内部写卡并保存操作状态 |

## 4. 易混淆概念

| 概念 | 区别 |
|---|---|
| 采集会话 | `sessionId` 关联一次完整任务；`DeviceRecordInfo.id` 仅跟踪一次设备写卡操作。 |
| 采集上传记录 | `CollectionRecord` 表示 App 本地片段文件及上传摘要，与设备内部写卡记录不是同一类数据。 |

## 5. 校验记录

- 术语来源：`ARCHITECTURE.md`。
- 注释/文档证据：核心标识、`CollectionMode`。
- 最近校验：2026-08-30。
