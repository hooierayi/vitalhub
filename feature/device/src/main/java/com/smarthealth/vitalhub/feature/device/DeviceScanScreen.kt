package com.smarthealth.vitalhub.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.*

@Composable
fun DeviceScanScreen(state: DeviceScanUiState, onProjectOnlyChanged: (Boolean) -> Unit, onRefresh: () -> Unit, onConnect: (String) -> Unit) {
    val visibleDevices = state.devices.filter { !state.projectOnly || it.inProject }
    FlowPage(scrollable = false) {
        ScanBanner(state.scanning)
        Row(Modifier.fillMaxWidth().height(55.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("仅显示本项目设备", Modifier.weight(1f), fontSize = 15.sp, color = VitalColors.TextPrimary)
            Switch(state.projectOnly, onProjectOnlyChanged, modifier = Modifier.size(49.dp, 30.dp), colors = SwitchDefaults.colors(checkedTrackColor = VitalColors.Teal, checkedThumbColor = Color.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("可用设备", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            Icon(Icons.Default.Refresh, "刷新", tint = VitalColors.TextSecondary, modifier = Modifier.size(21.dp).clickable(onClick = onRefresh))
        }
        visibleDevices.forEach { DeviceCard(it) { onConnect(it.id) } }
        state.flowError?.let { Text(it, color = VitalColors.Danger, fontSize = 14.sp) }
        SectionTitle("其他设备", top = 16.dp)
        Text("未发现其他设备", Modifier.fillMaxWidth().padding(top = 21.dp), textAlign = TextAlign.Center, fontSize = 14.sp, color = VitalColors.TextSecondary)
        Spacer(Modifier.weight(1f)); Box(Modifier.fillMaxWidth().height(1.dp).background(VitalColors.Border))
        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.Center) {
            Text("未找到设备？", fontSize = 14.sp, color = VitalColors.TextSecondary); Text(" 查看帮助", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = VitalColors.Teal)
        }
    }
}

@Composable
private fun ScanBanner(scanning: Boolean) {
    Row(Modifier.fillMaxWidth().background(VitalColors.BluePale, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("❪⌁❫", color = Color(0xFF62A9FF), fontSize = 31.sp, modifier = Modifier.padding(end = 14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (scanning) "正在扫描附近的设备…" else "扫描已完成", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            Text("请将设备靠近手机", fontSize = 13.sp, color = VitalColors.TextSecondary)
        }
    }
}

@Composable
private fun DeviceCard(device: ScannedDevice, onConnect: () -> Unit) {
    InfoCard(borderColor = Color(0xFF98D5D2), padding = PaddingValues(13.dp), spacing = 0.dp) {
        Row {
            Box(Modifier.size(40.dp, 58.dp).border(1.dp, VitalColors.TextSecondary, RoundedCornerShape(7.dp)))
            Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
                    if (device.inProject) Text("本项目设备", Modifier.padding(start = 10.dp).background(VitalColors.TealPale, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = VitalColors.Teal)
                }
                Text("SN：${device.serialNumber}", fontSize = 13.sp, color = VitalColors.TextSecondary)
                Text("信号：强        ${device.signalDbm} dBm", fontSize = 13.sp, color = VitalColors.TextSecondary)
                StatusProgress("电量：${device.battery}%", device.battery / 100f, VitalColors.Success)
                StatusProgress("存储：${device.storage}%", device.storage / 100f, VitalColors.Teal)
            }
        }
        Spacer(Modifier.height(12.dp)); FullWidthButton("连接", onClick = onConnect)
    }
}

@Composable
private fun StatusProgress(label: String, progress: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 10.dp), fontSize = 13.sp, color = VitalColors.TextSecondary)
        Box(Modifier.size(67.dp, 7.dp)) { ProgressTrack(progress, color, 7.dp) }
    }
}
