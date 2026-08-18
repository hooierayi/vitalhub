package com.smarthealth.vitalhub.feature.collection

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.navigation.CollectionMode
import com.smarthealth.vitalhub.core.ui.*

@Composable
fun CollectionScreen(state: CollectionUiState, onStartClip: () -> Unit, onStartContinuous: () -> Unit, onStopClip: () -> Unit, onFinishContinuous: () -> Unit) {
    when (state.mode) {
        CollectionMode.CLIP -> ClipPage(state, onStopClip)
        CollectionMode.CONTINUOUS -> ContinuousPage(state, onStartClip, onFinishContinuous)
        else -> PreviewPage(state, onStartClip, onStartContinuous)
    }
}

@Composable
private fun PreviewPage(state: CollectionUiState, onStartClip: () -> Unit, onStartContinuous: () -> Unit) {
    FlowPage(scrollable = false) {
        ConnectedBanner(state.device); Waveform("ECG", "增益：10 mm/mV", height = 95.dp); Waveform("RESP", "阻抗", true, 95.dp); Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            state.metrics.forEachIndexed { index, metric -> MetricCard(metric.name, metric.value, metric.unit, if (index == 0) VitalColors.Success else if (index == 1) VitalColors.Blue else VitalColors.Teal) }
            MetricCard("电量", state.device.battery.toString(), "%", VitalColors.Success)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowButton("采集 2 分钟", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onStartClip)
            FlowButton("连续记录", FlowButtonStyle.BLUE, Modifier.weight(1f), onStartContinuous)
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun ClipPage(state: CollectionUiState, onStop: () -> Unit) {
    FlowPage(scrollable = false) {
        Text("正在采集 2 分钟片段", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(state.clipProgress, Modifier.size(121.dp), color = VitalColors.Teal, trackColor = Color(0xFFECEFF2), strokeWidth = 9.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.clipElapsed, fontSize = 29.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary); Text("02:00", fontSize = 13.sp, color = VitalColors.TextSecondary) }
        }
        Waveform("ECG", height = 67.dp); Waveform("RESP", respiratory = true, height = 67.dp); Spacer(Modifier.height(14.dp))
        InfoCard(padding = PaddingValues(14.dp), spacing = 12.dp) { StatusLine(true, "本地缓存中", "已缓存 ${state.clipElapsed}"); StatusLine(false, "分块上传中", "2 / 4 块") }
        Spacer(Modifier.weight(1f)); FullWidthButton("停止采集", FlowButtonStyle.DANGER, onStop); Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun ContinuousPage(state: CollectionUiState, onClip: () -> Unit, onFinish: () -> Unit) {
    FlowPage(scrollable = false) {
        InfoCard(background = Color(0xFFF5FBF9), padding = PaddingValues(17.dp), spacing = 0.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(15.dp).background(VitalColors.Success, CircleShape)); Text("记录中", Modifier.padding(start = 10.dp), fontSize = 21.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary) }
            Text(state.recordingElapsed, Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center, fontSize = 37.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            KeyValueRow("记录编号", state.recordId); Spacer(Modifier.height(8.dp)); KeyValueRow("开始时间", state.startedAt); Spacer(Modifier.height(15.dp))
            InfoCard(padding = PaddingValues(14.dp), spacing = 10.dp) { KeyValueRow("设备", state.device.name); DeviceProgress("电量", "${state.device.battery}%", state.device.battery / 100f, VitalColors.Success); DeviceProgress("剩余存储", "${state.device.storage}%", state.device.storage / 100f, VitalColors.Teal) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 15.dp).background(Color(0xFFF4F7FB), RoundedCornerShape(9.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Info, null, tint = VitalColors.Blue, modifier = Modifier.size(23.dp)); Text("APP断开后设备仍会继续记录", Modifier.padding(start = 9.dp), fontSize = 14.sp, color = VitalColors.TextPrimary) }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) { FlowButton("采集 2 分钟片段", FlowButtonStyle.OUTLINE, Modifier.weight(1f), onClip); FlowButton("结束连续记录", FlowButtonStyle.DANGER, Modifier.weight(1f), onFinish) }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable private fun ConnectedBanner(device: ConnectedDevice) { Row(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFFF0F8F5), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(12.dp).background(VitalColors.Success, CircleShape)); Text("${device.name} 已连接", Modifier.padding(start = 9.dp).weight(1f), fontSize = 13.sp, color = VitalColors.TextPrimary); Text("电量：${device.battery}%", fontSize = 13.sp, color = VitalColors.TextSecondary) } }
@Composable private fun StatusLine(done: Boolean, label: String, value: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { if (done) Icon(Icons.Default.CheckCircle, null, tint = VitalColors.Teal, modifier = Modifier.size(22.dp)) else CircularProgressIndicator(.7f, Modifier.size(22.dp), color = VitalColors.Teal, strokeWidth = 2.dp, trackColor = Color(0xFFE2E8EC)); Text(label, Modifier.padding(start = 10.dp).weight(1f), fontSize = 14.sp, color = VitalColors.TextPrimary); Text(value, fontSize = 13.sp, color = VitalColors.TextSecondary) } }
@Composable private fun DeviceProgress(label: String, value: String, progress: Float, color: Color) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), fontSize = 14.sp, color = VitalColors.TextSecondary); Text(value, Modifier.padding(end = 10.dp), fontSize = 14.sp, color = VitalColors.TextPrimary); Box(Modifier.size(46.dp, 7.dp)) { ProgressTrack(progress, color, 7.dp) } } }
