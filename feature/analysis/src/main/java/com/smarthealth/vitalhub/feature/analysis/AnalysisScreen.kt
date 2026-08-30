package com.smarthealth.vitalhub.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.*
import kotlin.math.sin

@Composable
fun AnalysisScreen(state: AnalysisUiState, onDetails: () -> Unit) {
    FlowPage(scrollable = false) {
        UploadAndAnalysisCard(state)
        SuccessBanner(state.conclusion, state.suggestion)
        SectionTitle("关键指标趋势", top = 14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.metrics.forEach { TrendCard(it) } }
        SectionTitle("本次记录信息", top = 16.dp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { KeyValueRow("记录编号", state.recordId); KeyValueRow("记录时长", state.duration); KeyValueRow("记录时间", state.recordedAt); KeyValueRow("设备型号", state.deviceModel) }
        Spacer(Modifier.weight(1f)); FullWidthButton("查看详情", FlowButtonStyle.BLUE, onDetails); Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun UploadAndAnalysisCard(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F9F6), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (state.completed) VitalColors.Success else VitalColors.Teal,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    if (state.completed) "上传与分析完成" else "数据上传与分析中",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VitalColors.TextPrimary,
                )
                Text(
                    "采集编号 ${state.sessionId.ifBlank { "-" }}",
                    fontSize = 12.sp,
                    color = VitalColors.TextSecondary,
                )
            }
            Text(
                if (state.completed) "100%" else "处理中",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = VitalColors.Teal,
            )
        }
        ProgressTrack(progress = if (state.completed) 1f else 0.45f, color = VitalColors.Teal)
    }
}

@Composable private fun SuccessBanner(conclusion: String, suggestion: String) { Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFFF1F9F6), RoundedCornerShape(12.dp)).padding(horizontal = 21.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).background(Color(0xFFDDF4EC), CircleShape), contentAlignment = Alignment.Center) { ShieldCheckIcon() }; Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(conclusion, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = VitalColors.TextPrimary); Text(suggestion, fontSize = 13.sp, color = VitalColors.TextSecondary) } } }
@Composable private fun ShieldCheckIcon() { Box(Modifier.size(31.dp, 35.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize()) { val shield = Path().apply { moveTo(size.width / 2f, 0f); lineTo(size.width, size.height * .18f); lineTo(size.width * .9f, size.height * .7f); quadraticBezierTo(size.width * .72f, size.height * .9f, size.width / 2f, size.height); quadraticBezierTo(size.width * .28f, size.height * .9f, size.width * .1f, size.height * .7f); lineTo(0f, size.height * .18f); close() }; drawPath(shield, Color(0xFF07A766)) }; Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) } }
@Composable private fun RowScope.TrendCard(metric: AnalysisMetric) { Column(Modifier.weight(1f).height(94.dp).background(Color(0xFFF6F8FA), RoundedCornerShape(9.dp)).padding(10.dp)) { Text(metric.name, fontSize = 12.sp, color = VitalColors.TextSecondary); Row(verticalAlignment = Alignment.Bottom) { Text(metric.value, fontSize = 21.sp, fontWeight = FontWeight.Medium, color = VitalColors.TextPrimary); Text(" ${metric.unit}", fontSize = 10.sp, modifier = Modifier.padding(bottom = 3.dp)) }; Row(verticalAlignment = Alignment.CenterVertically) { Text(if (metric.normal) "正常" else "异常", fontSize = 10.sp, color = if (metric.normal) VitalColors.Teal else VitalColors.Danger); Canvas(Modifier.padding(start = 6.dp).weight(1f).height(21.dp)) { val path = Path(); for (i in 0..30) { val x = size.width * i / 30; val y = size.height / 2 + sin(i * .7).toFloat() * size.height * .14f; if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; drawPath(path, VitalColors.Blue, style = Stroke(1.7f)) } } } }
