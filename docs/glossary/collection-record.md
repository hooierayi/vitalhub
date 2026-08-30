# 采集记录

## 1. 定义

- 定义：一次正常完成的片段采集或连续记录的本地持久化摘要。
- 每条记录以 App 本地 `id` 唯一标识，并保存 `sessionId`、采集类型、记录时间、时长、本地文件路径、`userFingerprint` 和 `deviceAddress`。
- 只有完成采集才写入；实时预览、暂停或中途退出不构成完成记录。

## 2. 所属边界

- `:provider:record` 定义 `CollectionRecord` 与 `RecordProvider` 契约。
- `:feature:collection` 使用 Room 保存记录，并以 `/record/service` 注册 ARouter Provider。
- `:app` 的底栏记录页通过 Provider 观察全部记录，按记录时间倒序展示；用户名称通过 `UserInfoProvider` 解析，设备直接展示记录中的 `deviceAddress`，不依赖 Room 或 feature 实现类。
- 记录库不创建 `devices` 表；`deviceAddress` 直接保存并展示采集时的 MAC 地址。用户表归属 `:feature:user` 的独立 Room 数据库，因此 `userFingerprint` 是跨库逻辑关联，不声明 SQLite 外键。

## 3. 易混淆概念

| 概念 | 区别 |
|---|---|
| 采集会话 | `sessionId` 关联前后问卷和一次完整任务；一个记录通过它归属任务。 |
| 连续记录 | 是采集记录的一种类型；片段采集完成后也会形成采集记录。 |
