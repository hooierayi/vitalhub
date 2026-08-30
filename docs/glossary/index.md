# Global Business Glossary Index

## 1. 术语索引

| 术语 | 代码关键词 | 所属范围 | 相关模块 | 功能级文档 | 详情文件 |
|---|---|---|---|---|---|
| 采集会话 | `sessionId` | 一次用户采集任务及其关联数据 | home、questionnaire、device、collection、analysis | 待建立 | `docs/glossary/collection-session.md` |
| 连续记录 | `recordId`、`CONTINUOUS` | 记录仪连续采集文件 | collection | 待建立 | `docs/glossary/continuous-record.md` |
| 采集记录 | `CollectionRecord`、`RecordProvider` | 已完成片段或连续采集的持久化摘要 | app、collection、provider:record | `feature/collection/docs/biz/features/collection-flow.md` | `docs/glossary/collection-record.md` |
| 设备命令序号 | `cmdSeq` | 设备控制命令的确认、重试与幂等 | device、collection | 待建立 | `docs/glossary/device-command-sequence.md` |
| 用户资料 | `UserInfo` | 采集任务关联的姓名、性别与年龄 | home、user、provider:user | `feature/user/docs/biz/features/user-profile.md` | `docs/glossary/user-profile.md` |

## 2. 命中规则

- 优先使用已有文档、代码注释与业务命名中的中文叫法。
- 命中术语后读取详情文件，不在本索引展开长定义。
- 工程框架类概念不进入 glossary，放入 `docs/architecture/`。
