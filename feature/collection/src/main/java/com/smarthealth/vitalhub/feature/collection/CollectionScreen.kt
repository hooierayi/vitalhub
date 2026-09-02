package com.smarthealth.vitalhub.feature.collection

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.ui.*
import com.smarthealth.vitalhub.foundation.device.waveform.ui.EcgWaveform
import com.smarthealth.vitalhub.foundation.device.waveform.ui.PaperGain
import com.smarthealth.vitalhub.foundation.device.waveform.ui.PaperSpeed
import com.smarthealth.vitalhub.foundation.device.waveform.ui.RealtimeWaveformState
import com.smarthealth.vitalhub.foundation.device.waveform.ui.RespirationWaveform
import com.smarthealth.vitalhub.foundation.device.waveform.ui.waveformScaleLabel
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
    onPreview: () -> Unit,
    onStopClip: () -> Unit,
    onRestartClip: () -> Unit,
    onStartContinuousRecording: () -> Unit,
) {
    when (state.mode) {
        CollectionMode.CLIP -> ClipPage(
            state = state,
            device = device,
            isDeviceConnected = isDeviceConnected,
            ecgWaveformState = ecgWaveformState,
            respirationWaveformState = respirationWaveformState,
            latestFrame = latestFrame,
            onPreview = onPreview,
            onStop = onStopClip,
            onRestart = onRestartClip,
        )
        CollectionMode.CONTINUOUS -> ContinuousPage(
            state = state,
            device = device,
            onPreview = onPreview,
            onStart = onStartContinuousRecording,
        )
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
        LiveDataDashboard(
            ecgWaveformState = ecgWaveformState,
            respirationWaveformState = respirationWaveformState,
            latestFrame = latestFrame,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowButton("采集上传", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onStartClip)
            FlowButton("连续记录", FlowButtonStyle.PRIMARY, Modifier.weight(1f), onStartContinuous)
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun LiveDataDashboard(
    ecgWaveformState: RealtimeWaveformState,
    respirationWaveformState: RealtimeWaveformState,
    latestFrame: RecorderFrame?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
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
                trailing = "阻抗 · ${RESP_PAPER_SPEED.millimetersPerSecond} mm/s · AUTO",
                modifier = Modifier.weight(1f),
            ) {
                RespirationWaveform(
                    state = respirationWaveformState,
                    modifier = Modifier.fillMaxSize(),
                    paperSpeed = RESP_PAPER_SPEED,
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
}

@Composable
private fun WaveformPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    status: String? = null,
    statusColor: Color = VitalColors.TextMuted,
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
    device: BluetoothKitDevice?,
    isDeviceConnected: Boolean,
    ecgWaveformState: RealtimeWaveformState,
    respirationWaveformState: RealtimeWaveformState,
    latestFrame: RecorderFrame?,
    onPreview: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
) {
    FlowPage(scrollable = false) {
        ClipSummaryCard(state)
        state.flowError?.let {
            Text(it, Modifier.padding(top = 6.dp), color = VitalColors.Danger, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        ConnectedBanner(device, isDeviceConnected)
        Spacer(Modifier.height(8.dp))
        LiveDataDashboard(
            ecgWaveformState = ecgWaveformState,
            respirationWaveformState = respirationWaveformState,
            latestFrame = latestFrame,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealtimePreviewAction(onClick = onPreview)
            FlowButton(
                label = if (state.isClipCollecting) "停止采集" else "开始采集",
                style = if (state.isClipCollecting) FlowButtonStyle.DANGER else FlowButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f).height(51.dp),
                onClick = if (state.isClipCollecting) onStop else onRestart,
            )
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun RealtimePreviewAction(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .height(51.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(27.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.55f)
                lineTo(size.width * 0.22f, size.height * 0.55f)
                lineTo(size.width * 0.34f, size.height * 0.30f)
                lineTo(size.width * 0.48f, size.height * 0.78f)
                lineTo(size.width * 0.64f, size.height * 0.43f)
                lineTo(size.width * 0.76f, size.height * 0.55f)
                lineTo(size.width, size.height * 0.55f)
            }
            drawPath(
                path = path,
                color = VitalColors.Teal,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = "实时预览",
            color = VitalColors.Teal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ClipSummaryCard(state: CollectionUiState) {
    val durationClock = formatClipClock(state.clipDurationSeconds)
    val durationLabel = formatClipDurationLabel(state.clipDurationSeconds)
    val animatedProgress by animateFloatAsState(
        targetValue = state.clipProgress,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "clip-progress",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(VitalColors.TealPale, RoundedCornerShape(10.dp))
            .border(1.dp, VitalColors.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val compact = maxWidth < 350.dp
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(if (compact) 68.dp else 78.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.fillMaxSize(),
                    color = VitalColors.Teal,
                    trackColor = Color(0xFFE3E9EB),
                    strokeWidth = 7.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.clipElapsed,
                        fontSize = when {
                            durationClock.length > 5 -> 14.sp
                            compact -> 17.sp
                            else -> 20.sp
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = VitalColors.TextPrimary,
                    )
                    Text(durationClock, fontSize = 10.sp, color = VitalColors.TextSecondary)
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    if (state.isClipCollecting) "正在采集 $durationLabel 片段" else "等待开始采集",
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = VitalColors.TextPrimary,
                    maxLines = 1,
                )
                Text("剩余时间", fontSize = 11.sp, color = VitalColors.TextSecondary)
                Text(
                    "倒计时结束自动进入上传页面",
                    fontSize = 10.sp,
                    color = VitalColors.Teal,
                    maxLines = 1,
                )
            }
            Text(
                state.clipRemaining,
                fontSize = when {
                    durationClock.length > 5 -> 23.sp
                    compact -> 27.sp
                    else -> 31.sp
                },
                fontWeight = FontWeight.SemiBold,
                color = VitalColors.Teal,
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun ContinuousPage(
    state: CollectionUiState,
    device: BluetoothKitDevice?,
    onPreview: () -> Unit,
    onStart: () -> Unit,
) {
    FlowPage(scrollable = false) {
        InfoCard(background = Color(0xFFF5FBF9), padding = PaddingValues(17.dp), spacing = 0.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(15.dp).background(if (state.isContinuousRecording) VitalColors.Success else VitalColors.TextMuted, CircleShape)); Text(if (state.isContinuousRecording) "记录中" else "等待启动", Modifier.padding(start = 10.dp), fontSize = 21.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary) }
            Text(state.recordingElapsed, Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center, fontSize = 37.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            KeyValueRow("记录编号", state.recordId); Spacer(Modifier.height(8.dp)); KeyValueRow("开始时间", state.startedAt.ifBlank { "-" }); Spacer(Modifier.height(15.dp))
            InfoCard(padding = PaddingValues(14.dp), spacing = 10.dp) {
                KeyValueRow("设备", device?.bluetoothDevice?.name?.takeIf(String::isNotBlank) ?: "-")
                KeyValueRow("MAC", device?.key?.takeIf(String::isNotBlank) ?: "-")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 15.dp).background(Color(0xFFF4F7FB), RoundedCornerShape(9.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, null, tint = VitalColors.Blue, modifier = Modifier.size(23.dp)); Text("APP断开后设备仍会继续记录", Modifier.padding(start = 9.dp), fontSize = 14.sp, color = VitalColors.TextPrimary) }
        state.flowError?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = VitalColors.Danger,
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RealtimePreviewAction(onClick = onPreview)
            FlowButton(
                label = when {
                    state.isContinuousStartLoading -> "启动中"
                    state.isContinuousRecording -> "记录中（点击可重新启动记录）"
                    else -> "启动记录"
                },
                style = FlowButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f).height(51.dp),
                onClick = onStart,
                loading = state.isContinuousStartLoading,
            )
        }
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
private val ECG_PAPER_SPEED = PaperSpeed.MM_25_PER_SECOND
private val ECG_GAIN = PaperGain.MM_10_PER_MV
private val RESP_PAPER_SPEED = PaperSpeed.MM_6_25_PER_SECOND
