# :feature:user 架构索引

| 组件 | 作用 |
|---|---|
| `UserInfoEditFragment` | `/user/edit` 的 Fragment + ComposeView 入口 |
| `UserInfoEditViewModel` | 从 ARouter Provider 读取资料，维护创建/编辑表单状态 |
| `UserInfoProviderImpl` | `/user/service` 的 ARouter Provider，以 `:core:storage` 的 MMKV 后端持久化资料 |

页面不向其他 feature 暴露实现类；跨模块读取和保存只能依赖 `:provider:user` 中的接口。
