# 用户资料首次填写与修改

## 跨模块范围

| 模块 | 职责 |
|---|---|
| `:provider:user` | 定义 `UserInfo` 和 `UserInfoProvider` 契约 |
| `:feature:user` | 编辑页面、校验、Room 用户表和 Provider 实现 |
| `:feature:home` | 读取资料并展示引导卡或真实摘要 |
| `:app` | 根据目标 Fragment 声明的固定元数据展示应用壳标题 |

## 流程

1. 首页通过 ARouter 获取 `UserInfoProvider`。未读取到完整资料时，展示“用户信息未填写”引导卡；卡片和底部“填写用户信息”均进入 `/user/edit`。
2. 编辑页先显示“填写用户信息”，姓名和年龄为空、性别未选。保存前校验姓名、性别和 1–150 岁年龄，失败时保留用户输入并提示原因。
3. 保存时按规范化姓名、性别和年龄计算 SHA-256 指纹，在同一 Room 事务中将原 `active` 用户改为 `inactive`，再激活新指纹用户，完成后返回首页。
4. 编辑页通过 ARouter 获取 `UserInfoProvider` 并读取本地资料，在展示前自行声明“填写用户信息”或“修改用户信息”；不从首页传递任何资料状态或资料实体。

## 数据与风险

- Provider 只接受男或女；`UNSPECIFIED`、缺失字段、空姓名和非法年龄都不能构成有效资料。
- 不通过路由参数传递资料实体；页面和首页都从 Provider 获取最新数据。
- Provider 服务缺失时按无资料降级，避免展示伪造资料。
- `getUser()` 只返回 `active` 用户，`getUser(fingerprint)` 可恢复历史记录关联的 inactive 用户。
