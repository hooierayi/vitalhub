# Bluetooth

蓝牙基础组件，提供经典蓝牙与 BLE 的扫描、连接、读写和事件回调能力。

该模块从 Asclepius 的 `component_bluetooth` 迁移而来，统一使用
`com.smarthealth.vitalhub.foundation.bluetooth` 包名。

调用扫描、连接和读写 API 前，宿主必须完成对应的蓝牙运行时权限申请。组件 Manifest
已声明 Android 12 前后的蓝牙权限，但不负责展示权限 UI。
