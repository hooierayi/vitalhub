# VitalHub 持续采集 DICOM 设计

## 1. 已确认范围

模块使用 `dcm4che-core` 生成 DICOM Part 10 文件，不自行实现 DICOM 编码。最终格式固定为：

- Raw Data Storage SOP Class；
- Explicit VR Little Endian；
- ECG 和 RESP 使用标准 Waveform Sequence；
- 六轴、皮温、环境温度、环境湿度和汗液使用 `VITALHUB_DATA_V1` 私有字段；
- 一次连续记录默认产生一个 `.dcm`，仅达到技术容量上限时滚动为多个实例。

最终文件不记录设备帧序号、App 接收时间、连续性、设备协议版本、leadOff、Writer 内部
标识或 Frame Index。所有动态数据按创建时声明的固定采样率连续排列。

## 2. 两阶段写入

标准 DICOM Waveform Data 的最终长度在停止采集前无法确定，因此“持续写入”表示持续接收并
持久化采集值，不表示原地扩充一个已经封口的 `.dcm`：

```text
append(frame)
  ↓
内部可恢复 staging
  ↓ finish 或容量滚动
dcm4che 生成并校验 DICOM Part 10
  ↓
<instance>.dcm
```

staging 只用于崩溃恢复，不是交换格式，也不映射成最终 DICOM 私有字段。大块 Waveform Data
在封口时从 staging 流式读取，避免把整段记录一次性加载进内存。

## 3. 对外契约

接口按职责拆分在模块根 package `com.smarthealth.vitalhub.foundation.file.protocol`：

```text
ContinuousDicomWriter.kt       创建、恢复、append、checkpoint、finish、abort
DicomRecordingDefinition.kt   创建时固定的患者、Study、Series、设备与采集信息
AcquiredWearableFrame.kt      每秒追加的纯采集值
WearableSignalLayout.kt       固定采样率、位宽、符号、通道和缩放
DicomUidGenerator.kt          Study、Series、SOP UID 生成契约
DicomStorage.kt               工作区与最终文件发布目标
DicomWriterPolicy.kt          反压、checkpoint、容量滚动和校验策略
DicomWriterState.kt           生命周期状态
DicomWriterResult.kt          checkpoint 与最终文件结果
DicomWriteError.kt            定义、存储、编码、校验和发布错误
```

主写入接口：

```kotlin
interface ContinuousDicomWriter {
    val sessionId: String
    val state: StateFlow<DicomWriterState>
    val completedSegments: Flow<DicomSegmentResult>

    suspend fun append(frame: AcquiredWearableFrame): AppendReceipt
    suspend fun checkpoint(): DicomCheckpoint
    suspend fun finish(): DicomRecordingResult
    suspend fun abort(reason: AbortReason, retainWorkspace: Boolean)
}
```

`append` 有序且有反压。成功返回的 `AppendReceipt` 表示 Writer 已接管这一秒的数据，并告知
当前分段、累计接收帧数以及本次调用是否触发容量滚动。Receipt 只用于运行时反馈，不写入
最终 DICOM。`checkpoint()` 表示已达到可恢复的持久化点；只有 `finish()` 或容量滚动完成后
才产生可读取和上传的 `.dcm`。

```kotlin
data class AppendReceipt(
    val segmentNumber: Int,
    val acceptedFrameCount: Long,
    val rollover: RolloverResult?,
)

data class RolloverResult(
    val completedSegment: DicomSegmentResult,
    val nextSegmentNumber: Int,
)
```

## 4. 创建时固定信息

`DicomRecordingDefinition` 创建后不修改，包含：

- Patient Name、Patient ID、Issuer、出生日期、年龄和性别；只有年龄时写 Patient Age，不反推出生日期；
- Study Instance UID、Series Instance UID、Study ID、Series Number 和描述；
- 设备厂商、型号、序列号和软件版本；
- App 版本写入 Software Versions，格式为 `VitalHub Android/<versionName>`；
- Acquisition DateTime、时区以及明确提供时的同步信息；
- 信号布局、私有 Schema 与 Writer 策略。

ECG 通道来源使用 CID 3001 的 `MDC 2:0 Unspecified lead`，RESP 使用
`DCM 109117 Respiration Waveform`。设备量程已确认为 ECG 有符号 16 位对应
`-400..+400 mV`、RESP 有符号 24 位对应 `-200..+200 mV`，因此灵敏度分别写为
`400/32767 mV/count` 和 `200/8388607 mV/count`，Baseline 为 `0`，没有额外校准修正时
Correction Factor 为 `1`。当前没有应用滤波，四个滤波属性仅保留空值占位，不填写虚构频率。

Study UID 在一次业务 Study 中固定，Series UID 在一次连续记录中固定。每个最终 `.dcm`
使用新的 SOP Instance UID。默认生成策略采用 `2.25.<UUID 无符号十进制值>`，未来可以替换为
VitalHub 的组织 UID Root；UID 不承载或拼接业务含义。

Raw Data 的 Creator-Version UID 按私有 Schema 固定映射；`VITALHUB_DATA_V1` 使用固定 UID，
同一 Schema 不随文件或 App 小版本随机变化，只有不兼容的数据格式升级才分配新 UID。

每个 DICOM 文件必须是独立完整的 Part 10 文件，因此发生容量滚动后，固定 Patient、Study、
Series 等属性会在新文件中再次出现；它们不会随每秒采集帧重复出现。

## 5. 动态采集帧

每次 `append()` 接收一秒采集值：

```kotlin
data class AcquiredWearableFrame(
    val ecg: IntArray,
    val respiration: IntArray,
    val motion: List<WearableMotionSample>,
    val skinTemperatureRaw: Int,
    val ambientTemperatureRaw: Int,
    val ambientHumidityRaw: Int,
    val sweatLevel: SweatLevelValue,
)
```

接口统一使用 `Int` 承载整数。位宽不由 Kotlin `Short` 表达，而由固定布局明确：

| 数据 | 采样率 | 每帧数量 | DICOM 容器 | 有效位 | 解释 |
|---|---:|---:|---:|---:|---|
| ECG | 250 Hz | 250 | 16 | 16 | `SS` |
| RESP | 250 Hz | 250 | 32 | 24 | `SL` |
| 六轴 | 5 Hz | 5 组 | 16 | 16 | `SS` |
| 皮温 | 1 Hz | 1 | 16 | 16 | `SS`，scale 0.01 °C |
| 环境温度 | 1 Hz | 1 | 16 | 16 | `SS`，scale 0.01 °C |
| 环境湿度 | 1 Hz | 1 | 16 | 16 | `SS`，scale 0.01% |
| 汗液 | 1 Hz | 1 | 8 | 8 | `UB` |

设备线协议为大端；数据进入本接口时已经解析为整数。最终 DICOM 按 Explicit VR Little Endian
重新编码，不直接复制设备原始字节。

调用契约约定 ECG/RESP/六轴分别为 250/250/5 个样本，不在每次 `append()` 重复执行范围与
数量校验。设备协议解析层负责保证聚合帧结构正确。

汗液码表固定为：

```text
00=Unknown;01=NoSweat;02=Light;03=Medium;04=Heavy
```

## 6. 最终 DICOM 布局

### 标准部分

- SOP Class UID：`1.2.840.10008.5.1.4.1.1.66`（Raw Data Storage）；
- Transfer Syntax UID：`1.2.840.10008.1.2.1`（Explicit VR Little Endian）；
- ECG：Channel Label=`ECG`、16 Bits Allocated/Stored、`SS`；
- RESP：Channel Label=`RESP`、32 Bits Allocated、24 Bits Stored、`SL`；
- ECG：Sensitivity=`400/32767 mV/count`、有效范围 `-32768..32767`；
- RESP：Sensitivity=`200/8388607 mV/count`、有效范围 `-8388608..8388607`；
- 两个通道的 Correction Factor=`1`、Baseline=`0`，高通、低通、陷波中心频率和陷波带宽
  以零长度属性占位；
- ECG 固定展示建议为 `25 mm/s`、`10 mm/mV`、通道居中；Absolute Channel Display Scale
  由灵敏度乘以显示增益得到。RESP 的纵向增益由 App 按可见范围动态适配，不写固定展示尺度。

### `VITALHUB_DATA_V1` 私有部分

沿用参考文件中的字段位置保存：

- RESP 来源格式：24-bit、Big Endian；
- 六轴：`GYRO_X,GYRO_Y,GYRO_Z,ACC_X,ACC_Y,ACC_Z`；
- 皮温、环境温度、环境湿度：整段连续数组和实际 Sample Count；
- 汗液：整段连续 `UB` 数组、码表和实际 Sample Count。

汗液 `OB` 数据为奇数长度时由 DICOM 编码器补一个 `00`，读取端用 Sample Count 区分实际的
`UNKNOWN` 样本和结尾 padding。

## 7. 滚动、恢复与状态

滚动策略只使用预计文件容量，默认上限为可配置的 512 MiB，不按时间切分。滚动不拆分一次
`append()` 的聚合帧；所有分段共用 Study UID 和 Series UID，各自使用唯一 SOP Instance UID。

工作区可保存不进入最终文件的提交边界和校验信息，用于崩溃恢复。`finish()` 在同一 Writer
实例中幂等；已存在的目标文件不覆盖。状态为：

```text
CREATING → OPEN → FINALIZING → COMPLETED
             │          │
             ├→ FAILED ←─┘
             └→ ABORTED

FAILED(recoverable=true) → resume() → OPEN
```

## 8. 验收标准

- 单帧和多帧生成后可由 dcm4che 重新读取；
- ECG、RESP、六轴、温湿度和汗液逐值往返一致；
- 标准和私有 Sample Count 与实际 payload 长度一致；
- `UNKNOWN=0` 与奇数长度汗液 padding 可正确区分；
- 中断后可以从最后 checkpoint 恢复或明确拒绝；
- 重复 `finish()` 不产生重复 SOP Instance；
- 容量滚动不拆帧，分段 Instance Number 连续且 UID 唯一；
- 大文件封口内存占用不随全部波形长度线性增长；
- 独立 DICOM 验证器通过 Part 10、VR 和长度检查。
