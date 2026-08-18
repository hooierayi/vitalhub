# :feature:user - Agent Context

## 1. 模块定位

- 模块职责：提供用户资料填写/修改页面，并实现 `UserInfoProvider` 的本地 MMKV 服务。
- 所属业务域：采集前的用户资料登记。
- 本模块负责：表单状态与校验、`/user/edit` Fragment 路由、`/user/service` ARouter Provider 实现。
- 本模块不负责：首页摘要和问卷页面；它们只能通过 `:provider:user` 的接口读取资料。

## 2. 业务入口

| 入口类型 | 入口 | 说明 | 相关文档 |
|---|---|---|---|
| 页面路由 | `/user/edit` | 首次填写或后续修改用户资料 | `docs/biz/features/user-profile.md` |
| ARouter Provider | `/user/service` | `UserInfoProvider` 的 MMKV 实现 | `docs/biz/features/user-profile.md` |

## 3. 关键文件

| 文件 | 角色 |
|---|---|
| `UserInfoEditFragment.kt` | 页面路由和进入页面时确定的应用壳标题 |
| `UserInfoEditViewModel.kt` | 创建/编辑态、字段校验与保存 |
| `UserInfoProviderImpl.kt` | MMKV 读取、完整性判定和保存 |

## 4. 必须遵守

- 公开能力只通过 `:provider:user` 的 `UserInfoProvider` 暴露，并以 ARouter Provider 注册。
- 资料必须来自用户输入；姓名、性别、年龄均为必填，年龄范围为 1–150。
- 不保存或读取 `UNSPECIFIED` 性别；不完整资料一律作为无资料。
- 功能流程变更时同步 `docs/biz/features/user-profile.md` 与根 `docs/glossary/user-profile.md`。

## 5. 文档索引

| 类型 | 文档 |
|---|---|
| 业务域 | `docs/biz/index.md` |
| 功能流程 | `docs/biz/features/user-profile.md` |
| 模块术语 | `docs/glossary/index.md` |
