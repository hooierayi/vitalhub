# foundation:file-protocol

可交换文件协议基础模块，首要方向是持续采集数据的 DICOM `.dcm` 生成。

模块对外提供“创建会话、持续追加帧、检查点、恢复、封口”能力。追加期间数据写入可恢复的
staging 文件；在分段滚动或调用 `finish()` 时，才使用 `dcm4che-core` 生成最终 DICOM Part 10
文件。对调用方而言是持续写入，但不尝试原地增长已编码的 `Waveform Data (5400,1010)`。

设计详情见 [DESIGN.md](DESIGN.md)。
当前已实现按职责拆分的固定定义、纯采集数据帧、信号位宽、UUID 派生 UID、本地 Publisher、
持续 Writer、staging 和 dcm4che DICOM 生成。创建时确定的
Patient/Study/Series/设备/信号信息不随采集帧重复传入，动态帧只包含 ECG、RESP、六轴、
温湿度和汗液值。首版支持 checkpoint、容量滚动、Part 10 生成与回读验证；进程终止后的
workspace 恢复尚未实现，`inspect/resume` 会明确报告不可恢复。

## 模块边界

- 接收文件协议自身的元数据与可稳定序列化的聚合帧，不直接依赖 `device-api`、BLE 或业务 Provider。
- 不接收 `Activity`、`Context` 或 `Uri`；Android 文件位置由调用方适配成工作目录和发布目标。
- 只使用 `dcm4che-core` 负责 DICOM 数据集、VR、标签和 Part 10 编码，不自研 DICOM 编码器。
- staging 是模块内部、带版本的恢复格式，不是对外交换格式。
- 图像编解码、DIMSE/DICOMweb 网络、PACS 归档和业务上传不属于本模块。

## 依赖

引入的 `dcm4che-core` 版本为 `5.35.1`，许可证可选 MPL 1.1、GPL 2.0 或 LGPL 2.1，发布前仍需
完成项目级第三方许可证审核与 NOTICE 归档。依赖从 dcm4che 官方 Maven 仓库解析；其父 POM
引用的 Weasis BOM 按上游配置从 nroduit Maven 仓库解析，两处仓库均通过 Gradle content filter 限定 group。
