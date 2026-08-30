# 记录仪设备 SDK 架构

## 1. 边界

`:foundation:bluetooth` 只提供扫描、连接、通知和写入等底层能力，不识别设备协议。
`:foundation:device-sdk` 是聚合壳，业务层只持有 `DeviceSdk` / `DeviceSession`，不组装传输、拆包、指令、波形或文件实现。

```text
feature:collection
  └─ DeviceSession (device-api)
       ├─ connect(address) / disconnect / execute / startRecording / stopRecording
       ├─ metrics / recordingState
       ├─ ecgWaveforms / respirationWaveforms
       └─ frames

device-sdk（会话编排与自动分发）
  ├─ device-transport ──► foundation:bluetooth
  ├─ device-protocol
  ├─ device-command ────► device-transport
  ├─ device-storage
  └─ device-waveform
```

各模块均使用 `implementation`。`:foundation:device-api` 只放稳定契约和聚合帧模型；具体实现不向业务暴露。

## 2. 数据链路

```text
BLE notification: ByteArray（长度任意，可能半帧/多帧/带脏数据）
  ↓ DeviceTransport.incomingBytes（单连接有序收集）
ExpandableRingByteBuffer（环形队列、自动扩容、有最大容量）
  ↓ ProtocolStreamEngine（统一循环和读游标提交）
Interceptor Pipeline
  1. HeaderSync：寻找 AA AA / 5A A5，丢弃帧头前脏数据
  2. FrameBoundary：数据帧取配置长度；命令帧读取长度字段
  3. XorIntegrity：校验候选帧
  4. SequenceContinuity：FIRST / CONTINUOUS / GAP / DUPLICATE / OUT_OF_ORDER
  5. RecorderPacketDecoder：路由并解析聚合数据帧、回执或事件
  ↓ ProtocolPacket.Data(RecorderFrame)
DeviceSession.distribute（同一完整帧自动扇出）
  ├─ waveform：分别投影 EcgWaveformFrame / RespirationWaveformFrame；小缓冲、慢 UI 丢最旧帧
  ├─ storage：RecorderFrame；有界 Channel 反压，不静默丢记录帧
  ├─ metrics：DeviceMetrics；StateFlow 只保留最新温湿度/导联等状态
  └─ frames：需要完整帧的领域消费者
```

拦截器不修改环形缓冲的读游标，只返回：

- `NeedMoreData`：保留当前数据，等待下一段通知；
- `Emit(packet, consumedBytes)`：外层提交消费并继续解析缓冲中的下一帧；
- `Recover(skipBytes, reason)`：外层按明确字节数恢复并重新寻找帧头。

因此仍保留 Asclepius 流式处理中的关键行为：不足一帧不消费、错误后重新同步、一次通知连续产出多帧、序号连续性独立判断；但缓冲管理和协议步骤通过统一外层约束，不依赖固定五个实现类。

## 3. 聚合帧

当前草案的固定数据帧长度为 1323 字节，解析结果保持为一个 `RecorderFrame`：

```text
metadata(sequence, receivedAtMillis, continuity, protocolVersion)
ecg: IntArray(250)                  // 16 位有符号大端解析结果
respiration: IntArray(250)          // 24 位有符号大端解析结果
temperature(skin, ambient, humidity)
motion: List<MotionSample>(5)       // 每项陀螺仪 XYZ + 加速度 XYZ
sweatLevel
leadOff
```

ECG、呼吸仍是同一个设备帧中的类型化数据块；存储写入解析后的完整聚合帧并保留设备真实序号。波形模块为 UI 分别投影两个对象和数据流，使两种信号可以独立消费、配置和绘制，二者继续携带相同的设备帧序号与连续性。

## 4. 波形绘制链路

```text
RecorderFrame
  ↓ device-waveform
EcgWaveformFrame / RespirationWaveformFrame
  ↓ feature:collection 映射连续性
RealtimeWaveformState.append(IntArray)
  ↓ 固定容量 IntArray 环形缓冲
按 250 Hz 推进的显示游标（屏幕 VSync 刷新）
  ↓ foundation:device-waveform-ui
物理图纸网格 + Sweep / Scroll + ECG / 呼吸波形
```

`:foundation:device-waveform-ui` 不依赖 `DeviceSession` 或 `FrameContinuity`。View 首次创建时从第一个采样起笔；数据中断时显示游标在已有数据末尾等待，新数组到达后继续当前轨迹，只有 View 重建或显式 `clear()` 才重新从头开始。重复帧不送入绘制缓冲。组件使用 `xdpi / ydpi` 将毫米换算为像素，并接受横纵校准系数修正设备上报误差。标准配置为 250 Hz、25 mm/s、10 mm/mV，此时 10 个采样点对应横向 1 mm。接收端可一次追加 250 点，显示游标仍按采样率平滑消费，避免每秒整段跳变。

纵向显示借鉴 Asclepius 的坐标原点与增益分离策略：采样环形缓冲始终保存协议解析后的原始有符号整数，近期分包极值只用于计算绘图视口中心，不对采样数组做基线相减，也不做滤波。视口每 500 个采样使用最近 8 个分包的截尾极值重算，降低单个尖峰造成的跳动。ECG 使用 `WaveformGainMode.FIXED` 保持配置的真实图纸增益；`FIT_STANDARD_GAIN` 可在 20、10、5 mm/mV 中选择标准档位。呼吸波形不按心电图纸显示，隐藏毫米网格和校准脉冲，使用 `FIT_VISIBLE_RANGE` 将近期振幅连续适配到可用高度。

## 5. 指令链路

```text
DeviceSession.execute(DeviceCommand)
  ↓ PriorityCommandQueue（有界；Stop 等控制指令可高优先级）
CommandWorker（单协程，严格单飞）
  ↓ RecorderCommandEncoder
DeviceTransport.write(ByteArray)
  ↓ 等待同一解析管线产出的 ProtocolPacket.Receipt
只匹配 `RecorderCommandRegistry` 注册的 responseCode + timeout
  ↓
CommandResult.Success / Rejected / Failed
```

协议没有请求 ID，因此同类或不同类指令均不并发。写成功只代表 BLE 写入完成，不代表设备执行成功。重试发生在当前队列项内部，且只有显式标记幂等的指令允许多次发送；断连会取消当前及排队请求。无法从协议层彻底消除“超时后到达、且与下一条同类型命令相同”的迟到回执歧义，因此上层仍应避免短时间重复提交同类型非幂等指令。

## 6. 文件链路

`BinaryFrameRecorder` 使用独立写入协程和有界 Channel。`append` 在队列满时产生反压，不会静默丢帧；写入异常只把 `recordingState` 置为 `Failed`，不会终止协议解析和画图。

文件先写 `<target>.part`，正常停止并 flush/close 后才重命名为目标文件。目标文件已存在或残留 `.part` 时拒绝覆盖。当前内部格式以 `VHF + version` 开头，每条记录保存 metadata 和所有聚合块；在服务端文件协议确定前，此格式只作为本地 SDK 版本化容器。

## 7. 接入前必须确认的设备配置

`RecorderDeviceSdk.create(RecorderDeviceSdkConfig)` 要求调用方明确提供：

- GATT service UUID；
- notify characteristic UUID；
- write characteristic UUID；
- 聚合帧标量字节序；
- XOR 是否包含帧头。

`:feature:collection` 已显式配置记录仪 GATT profile：服务 `0000FFF0-0000-1000-8000-00805F9B34FB`、通知特征 `0000FFF1-0000-1000-8000-00805F9B34FB`、写特征 `0000FFF2-0000-1000-8000-00805F9B34FB`，接收类型为 `NOTIFY`。通知使用标准 CCCD `00002902-0000-1000-8000-00805F9B34FB`，即 `useNotificationDescriptor=false`；原生通知开关仍为 `enable=true`。扫描仍由 feature 发起，连接、数据通道启用和断开由 Activity 级 `DeviceSession` 管理；SDK 保留 `createWithGattDiscovery` 作为未知 profile 设备的调试入口，但当前采集链路不使用它。标量按协议文档使用大端，全帧执行 XOR。后续长时间采集再将同一个会话所有权迁移到前台采集 Service，Fragment/ViewModel 只观察会话数据和调用命令。

真机调试时统一过滤 Logcat 标签 `RecorderDataChain`。日志使用 `[GATT]`、`[BLE_RX]`、`[BUFFER]`、`[PROTOCOL]`、`[RECOVERY]`、`[COMMAND]`、`[WAVEFORM]`、`[FILE]`、`[DISPATCH]` 分段标记连接、原始分包、环形缓冲、协议拆包、错误恢复、指令、波形、文件和消费分发。原始接收数据仅打印长度与前 16 字节，命令发送只打印前 4 字节，避免完整聚合帧和连续采集用户信息进入日志。SDK 默认关闭该日志，当前采集模块通过 `dataChainTraceEnabled=true` 为联调显式开启。

debug APK 同时注册 DoKit 悬浮入口的“蓝牙链路”“协议交互”“波形缓冲”三个自定义工具。点击后会在当前宿主 Activity 内打开可拖动、可关闭、可同时存在的全宽数据卡片，“详情”再进入完整面板；该模式不依赖 `SYSTEM_ALERT_WINDOW` 权限。完整 BLE RX/TX 字节仅保存在进程内有界调试队列并在蓝牙面板展示，不写入 Logcat 或磁盘；协议面板固定展示缓冲、拆包、解码包头尾、恢复、指令和分发状态，并独立保留各阶段的最新事件；波形面板固定展示 ECG/呼吸环形缓冲状态以及最近追加采样的头尾。以上 DoKit 模块均通过 `debugImplementation` 接入，release APK 不包含 DoKit 依赖、悬浮卡片和调试 Activity。
