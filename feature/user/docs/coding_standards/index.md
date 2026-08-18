# :feature:user 编码约束

- 业务页面保持 Fragment 作为路由入口、ComposeView 承载内容。
- 调用跨模块资料能力时通过 `ARouter.navigation(UserInfoProvider::class.java)` 获取接口。
- Provider 实现保存在本模块，命名为 `UserInfoProviderImpl`，初始化只持有 application context 创建的存储。
- 表单默认值不得伪造用户资料；空态必须提供填写入口。
