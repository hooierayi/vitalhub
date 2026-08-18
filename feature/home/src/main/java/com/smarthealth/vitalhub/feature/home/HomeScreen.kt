package com.smarthealth.vitalhub.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.*

@Composable
fun HomeScreen(state: HomeUiState, onStartQuestionnaire: () -> Unit) {
    FlowPage(scrollable = false, navigationSafe = false, bottomBarSafe = true) {
        PatientCard(state.patient)
        Spacer(Modifier.height(12.dp))
        DeviceStatusCard(state.device)
        SectionTitle("采集流程", "${state.completedSteps} / ${state.steps.size} 步完成", top = 15.dp)
        InfoCard(padding = PaddingValues(vertical = 5.dp, horizontal = 14.dp), spacing = 0.dp) {
            state.steps.forEach { ProcessStep(it) }
        }
        Spacer(Modifier.weight(1f))
        FullWidthButton("填写采集前问卷", onClick = onStartQuestionnaire)
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun PatientCard(patient: PatientSummary) {
    InfoCard(padding = PaddingValues(15.dp), spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(55.dp).background(VitalColors.TealPale, CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(18.dp).background(VitalColors.Teal, CircleShape))
                    Box(Modifier.size(31.dp, 15.dp).background(VitalColors.Teal, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${patient.name}   ${patient.gender}   ${patient.age}岁", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary)
                Text("患者ID：${patient.patientId}", fontSize = 13.sp, color = VitalColors.TextSecondary)
                Text("项目：${patient.project}", fontSize = 13.sp, color = VitalColors.TextSecondary)
            }
            Icon(Icons.Outlined.Edit, "编辑", tint = VitalColors.TextSecondary, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun DeviceStatusCard(device: DeviceSummary) {
    InfoCard(padding = PaddingValues(15.dp), spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp, 60.dp).border(1.dp, VitalColors.TextSecondary, RoundedCornerShape(8.dp)))
            Column(Modifier.padding(start = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${device.name}连接状态", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
                Text(if (device.connected) "设备已连接" else "请先连接设备", fontSize = 13.sp, color = VitalColors.TextSecondary)
            }
            Text(if (device.connected) "已连接" else "未连接", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (device.connected) VitalColors.Success else VitalColors.Danger)
        }
    }
}

@Composable
private fun ProcessStep(step: CollectionStep) {
    Row(Modifier.fillMaxWidth().height(63.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(35.dp).background(VitalColors.Teal, CircleShape), contentAlignment = Alignment.Center) {
            Text(step.number.toString(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary)
            Text(step.subtitle, fontSize = 12.sp, color = VitalColors.TextSecondary)
        }
        Text(if (step.completed) "✓" else "›", fontSize = 25.sp, color = if (step.completed) VitalColors.Success else VitalColors.TextMuted)
    }
}
