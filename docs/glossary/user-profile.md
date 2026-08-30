# 用户资料

- 代码关键词：`UserInfo`、`UserInfoProvider`。
- 定义：参与采集人员由本人填写的姓名、性别和年龄；不提供演示资料或预填默认值。
- 所属模块：`:provider:user` 定义继承 `IProvider` 的数据模型、指纹和读取/保存契约；`:feature:user` 提供编辑页面、Room 实现及 `/user/service` 服务注册；`:feature:home` 通过 ARouter 读取并展示资料摘要。
- 存储：`users` 表以规范化姓名、性别、年龄计算的 SHA-256 `fingerprint` 为主键，并以 `active` / `inactive` 标识当前资料。保存新资料时在同一 Room 事务中停用原活跃用户并激活新指纹。性别必须为男或女，缺项、非法年龄或 `UNSPECIFIED` 均按无资料处理。
- 流程：首页无资料时展示引导卡，卡片与底部“填写用户信息”均可进入填写页；保存后回到首页展示真实摘要并恢复采集前问卷入口。后续进入编辑页会回填资料并显示“修改用户信息”。详情见 `feature/user/docs/biz/features/user-profile.md`。
