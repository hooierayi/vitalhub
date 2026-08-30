package com.smarthealth.vitalhub.feature.collection

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.ui.*
import com.smarthealth.vitalhub.core.waveform.EcgWaveform
import com.smarthealth.vitalhub.core.waveform.PaperGain
import com.smarthealth.vitalhub.core.waveform.PaperSpeed
import com.smarthealth.vitalhub.core.waveform.RealtimeWaveformState
import com.smarthealth.vitalhub.core.waveform.RespirationWaveform
import com.smarthealth.vitalhub.core.waveform.waveformScaleLabel
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.foundation.device.api.MotionSample
import com.smarthealth.vitalhub.foundation.device.api.RecorderFrame
import com.smarthealth.vitalhub.foundation.device.api.SweatLevel

@Composable
fun CollectionScreen(
    state: CollectionUiState,
    device: BluetoothKitDevice?,
    isDeviceConnected: Boolean,
    ecgWaveformState: RealtimeWaveformState,
    respirationWaveformState: RealtimeWaveformState,
    latestFrame: RecorderFrame?,
    onStartClip: () -> Unit,
    onStartContinuous: () -> Unit,
    onStopClip: () -> Unit,
    onFinishContinuous: () -> Unit,
) {
    when (state.mode) {
        CollectionMode.CLIP -> ClipPage(state, ecgWaveformState, respirationWaveformState, onStopClip)
        CollectionMode.CONTINUOUS -> ContinuousPage(state, device, onStartClip, onFinishContinuous)
        else -> PreviewPage(
            state,
            device,
            isDeviceConnected,
            ecgWaveformState,
            respirationWaveformState,
            latestFrame,
            onStartClip,
            onStartContinuous,
        )
    }
}

@Composable
private fun PreviewPage(
    state: CollectionUiState,
    device: BluetoothKitDevice?,
    isDeviceConnected: Boolean,
    ecgWaveformState: RealtimeWaveformState,
    respirationWaveformState: RealtimeWaveformState,
    latestFrame: RecorderFrame?,
    onStartClip: () -> Unit,
    onStartContinuous: () -> Unit,
) {
    FlowPage(scrollable = false) {
        ConnectedBanner(device, isDeviceConnected)
        state.flowError?.let {
            Text(it, Modifier.padding(top = 6.dp), color = VitalColors.Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1.95f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WaveformPanel(
                    title = "ECG",
                    trailing = waveformScaleLabel(ECG_PAPER_SPEED, ECG_GAIN),
                    status = when (latestFrame?.leadOff) {
                        false -> "导联正常"
                        true -> "导联脱落"
                        null -> "导联--"
                    },
                    statusColor = when (latestFrame?.leadOff) {
                        false -> VitalColors.Success
                        true -> VitalColors.Danger
                        null -> VitalColors.TextMuted
                    },
                    modifier = Modifier.weight(1.25f),
                ) {
                    EcgWaveform(
                        state = ecgWaveformState,
                        modifier = Modifier.fillMaxSize(),
                        paperSpeed = ECG_PAPER_SPEED,
                        gain = ECG_GAIN,
                    )
                }
                WaveformPanel(
                    title = "RESP",
                    trailing = "阻抗",
                    modifier = Modifier.weight(1f),
                ) {
                    RespirationWaveform(
                        state = respirationWaveformState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SixAxisPanel(
                    sample = latestFrame?.motion?.lastOrNull(),
                    modifier = Modifier.weight(1.25f),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EnvironmentPanel(
                        frame = latestFrame,
                        modifier = Modifier.weight(1f),
                    )
                    BodyPanel(
                        frame = latestFrame,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowButton("采集上传", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onStartClip)
            FlowButton("连续记录", FlowButtonStyle.BLUE, Modifier.weight(1f), onStartContinuous)
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun WaveformPanel(
    title: String,
    trailing: String? = null,
    status: String? = null,
    statusColor: Color = VitalColors.TextMuted,
    modifier: Modifier = Modifier,
    waveform: @Composable () -> Unit,
) {
    PreviewCard(modifier) {
        PreviewPanelHeader(title, trailing, status, statusColor)
        Box(Modifier.fillMaxWidth().weight(1f)) { waveform() }
    }
}

@Composable
private fun SixAxisPanel(sample: MotionSample?, modifier: Modifier = Modifier) {
    val rows = listOf(
        "Gx" to sample?.gyroX?.toString(),
        "Gy" to sample?.gyroY?.toString(),
        "Gz" to sample?.gyroZ?.toString(),
        "Ax" to sample?.accelerationX?.toString(),
        "Ay" to sample?.accelerationY?.toString(),
        "Az" to sample?.accelerationZ?.toString(),
    )
    PreviewDataPanel("六轴数据", rows, "raw", modifier)
}

@Composable
private fun EnvironmentPanel(frame: RecorderFrame?, modifier: Modifier = Modifier) {
    val temperature = frame?.temperature
    PreviewDataPanel(
        title = "环境",
        rows = listOf(
            "湿度" to temperature?.humidityPercent?.formatOneDecimal(),
            "温度" to temperature?.ambientCelsius?.formatOneDecimal(),
        ),
        units = listOf("%", "℃"),
        modifier = modifier,
    )
}

@Composable
private fun BodyPanel(frame: RecorderFrame?, modifier: Modifier = Modifier) {
    PreviewDataPanel(
        title = "人体",
        rows = listOf(
            "皮温" to frame?.temperature?.skinCelsius?.formatOneDecimal(),
            "汗液" to frame?.sweatLevel?.displayName(),
        ),
        units = listOf("℃", ""),
        modifier = modifier,
    )
}

@Composable
private fun PreviewDataPanel(
    title: String,
    rows: List<Pair<String, String?>>,
    unit: String,
    modifier: Modifier = Modifier,
) = PreviewDataPanel(title, rows, List(rows.size) { unit }, modifier)

@Composable
private fun PreviewDataPanel(
    title: String,
    rows: List<Pair<String, String?>>,
    units: List<String>,
    modifier: Modifier = Modifier,
) {
    PreviewCard(modifier) {
        PreviewPanelHeader(title)
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontSize = 12.sp, color = VitalColors.TextPrimary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value ?: "-", fontSize = 12.sp, color = VitalColors.TextPrimary)
                        Text(
                            units.getOrElse(index) { "" },
                            Modifier.padding(start = 3.dp),
                            fontSize = 10.sp,
                            color = VitalColors.TextSecondary,
                        )
                    }
                }
                if (index != rows.lastIndex) {
                    Divider(color = VitalColors.Border.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VitalColors.Surface)
            .border(1.dp, VitalColors.Border, shape),
        content = content,
    )
}

@Composable
private fun PreviewPanelHeader(
    title: String,
    trailing: String? = null,
    status: String? = null,
    statusColor: Color = VitalColors.TextMuted,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
            status?.let {
                Row(
                    modifier = Modifier.padding(start = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(statusColor, CircleShape))
                    Text(
                        it,
                        Modifier.padding(start = 4.dp),
                        fontSize = 11.sp,
                        color = statusColor,
                        maxLines = 1,
                    )
                }
            }
        }
        trailing?.let {
            Text(it, fontSize = 11.sp, color = VitalColors.TextMuted, maxLines = 1)
        }
    }
}

private fun Double.formatOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)

private fun SweatLevel.displayName(): String = when (this) {
    SweatLevel.NONE -> "无"
    SweatLevel.LIGHT -> "微汗"
    SweatLevel.MEDIUM -> "中汗"
    SweatLevel.HEAVY -> "大汗"
    SweatLevel.UNKNOWN -> "-"
}

@Composable
private fun ClipPage(
    state: CollectionUiState,
    ecgWaveformState: RealtimeWaveformState,
    respirationWaveformState: RealtimeWaveformState,
    onStop: () -> Unit,
) {
    FlowPage(scrollable = false) {
        Text("正在采集 2 分钟片段", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        state.flowError?.let { Text(it, color = VitalColors.Danger, fontSize = 14.sp) }
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(state.clipProgress, Modifier.size(121.dp), color = VitalColors.Teal, trackColor = Color(0xFFECEFF2), strokeWidth = 9.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.clipElapsed, fontSize = 29.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary); Text("02:00", fontSize = 13.sp, color = VitalColors.TextSecondary) }
        }
        WaveformHeader("ECG", waveformScaleLabel(ECG_PAPER_SPEED, ECG_GAIN))
        EcgWaveform(
            state = ecgWaveformState,
            modifier = Modifier.fillMaxWidth().height(67.dp),
            paperSpeed = ECG_PAPER_SPEED,
            gain = ECG_GAIN,
            showCalibrationPulse = false,
        )
        WaveformHeader("RESP", "250 Hz · 自动幅度")
        RespirationWaveform(
            state = respirationWaveformState,
            modifier = Modifier.fillMaxWidth().height(67.dp),
        )
        Spacer(Modifier.height(14.dp))
        InfoCard(padding = PaddingValues(14.dp), spacing = 12.dp) { StatusLine(true, "本地缓存中", "已缓存 ${state.clipElapsed}"); StatusLine(false, "分块上传中", "2 / 4 块") }
        Spacer(Modifier.weight(1f)); FullWidthButton("停止采集", FlowButtonStyle.DANGER, onStop); Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun WaveformHeader(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        Text(trailing, fontSize = 12.sp, color = VitalColors.TextSecondary)
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ContinuousPage(state: CollectionUiState, device: BluetoothKitDevice?, onClip: () -> Unit, onFinish: () -> Unit) {
    FlowPage(scrollable = false) {
        state.flowError?.let { Text(it, color = VitalColors.Danger, fontSize = 14.sp) }
        InfoCard(background = Color(0xFFF5FBF9), padding = PaddingValues(17.dp), spacing = 0.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(15.dp).background(VitalColors.Success, CircleShape)); Text("记录中", Modifier.padding(start = 10.dp), fontSize = 21.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary) }
            Text(state.recordingElapsed, Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center, fontSize = 37.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            KeyValueRow("记录编号", state.recordId); Spacer(Modifier.height(8.dp)); KeyValueRow("开始时间", state.startedAt); Spacer(Modifier.height(15.dp))
            InfoCard(padding = PaddingValues(14.dp), spacing = 10.dp) {
                KeyValueRow("设备", device?.bluetoothDevice?.name?.takeIf(String::isNotBlank) ?: "-")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 15.dp).background(Color(0xFFF4F7FB), RoundedCornerShape(9.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, null, tint = VitalColors.Blue, modifier = Modifier.size(23.dp)); Text("APP断开后设备仍会继续记录", Modifier.padding(start = 9.dp), fontSize = 14.sp, color = VitalColors.TextPrimary) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) { FlowButton("采集 2 分钟片段", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onClip); FlowButton("结束连续记录", FlowButtonStyle.DANGER, Modifier.weight(1f), onFinish) }
        Spacer(Modifier.height(9.dp))
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ConnectedBanner(device: BluetoothKitDevice?, isConnected: Boolean) {
    val statusColor = if (isConnected) VitalColors.Success else VitalColors.Danger
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(VitalColors.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, VitalColors.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(statusColor, CircleShape))
        Column(
            modifier = Modifier
                .padding(start = 13.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                device?.bluetoothDevice?.name?.takeIf(String::isNotBlank) ?: "-",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = VitalColors.TextPrimary,
            )
            Text(
                device?.key?.takeIf(String::isNotBlank) ?: "-",
                fontSize = 12.sp,
                color = VitalColors.TextSecondary,
            )
        }
        Text(
            text = if (isConnected) "已连接" else "已断开",
            fontSize = 14.sp,
            color = statusColor,
        )
    }
}
@Composable private fun StatusLine(done: Boolean, label: String, value: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { if (done) Icon(Icons.Default.CheckCircle, null, tint = VitalColors.Teal, modifier = Modifier.size(22.dp)) else CircularProgressIndicator(.7f, Modifier.size(22.dp), color = VitalColors.Teal, strokeWidth = 2.dp, trackColor = Color(0xFFE2E8EC)); Text(label, Modifier.padding(start = 10.dp).weight(1f), fontSize = 14.sp, color = VitalColors.TextPrimary); Text(value, fontSize = 13.sp, color = VitalColors.TextSecondary) } }

private val ECG_PAPER_SPEED = PaperSpeed.MM_25_PER_SECOND
private val ECG_GAIN = PaperGain.MM_10_PER_MV
