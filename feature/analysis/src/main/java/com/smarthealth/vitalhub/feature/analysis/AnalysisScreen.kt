package com.smarthealth.vitalhub.feature.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.ui.FlowButton
import com.smarthealth.vitalhub.core.ui.FlowButtonStyle
import com.smarthealth.vitalhub.core.ui.ProgressTrack
import com.smarthealth.vitalhub.core.ui.VitalColors

private val AnalysisCardShape = RoundedCornerShape(10.dp)
private val AnalysisCardBorder = Color(0xFFD7E1E7)
private val AnalysisMintBorder = Color(0xFFBFE4DC)
private val AnalysisMintSurface = Color(0xFFF1F9F6)
private val MetricTrendColor = Color(0xFF08A47E)
private val RecordIconColor = Color(0xFF2E8FF4)

@Composable
fun AnalysisScreen(
    state: AnalysisUiState,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onPostQuestionnaire: () -> Unit,
) {
    Surface(color = VitalColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 20.dp),
            ) {
                UploadStatusCard(state)
                SectionHeading("本次记录信息", top = 24.dp)
                RecordInformationCard(state)
                if (state.completed) {
                    SectionHeading("关键指标趋势", top = 24.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.metrics.forEachIndexed { index, metric ->
                            MetricTrendCard(metric = metric, index = index)
                        }
                    }
                }
            }
            Surface(color = Color.White, shadowElevation = 5.dp) {
                AnalysisActions(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    onHome = onHome,
                    onRetry = onRetry,
                    onPostQuestionnaire = onPostQuestionnaire,
                    processStage = state.processStage,
                )
            }
        }
    }
}

@Composable
private fun UploadStatusCard(state: AnalysisUiState) {
    val visual = processVisual(state)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.surfaceColor, AnalysisCardShape)
            .border(1.dp, visual.borderColor, AnalysisCardShape)
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(visual.accentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(31.dp),
                )
            }
            Column(
                modifier = Modifier.padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = visual.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VitalColors.TextPrimary,
                )
                Text(
                    text = "片段采集：${state.recordId.ifBlank { "-" }}",
                    fontSize = 13.sp,
                    color = VitalColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier.height(40.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = visual.statusText,
                color = visual.accentColor,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(
            text = visual.description,
            modifier = Modifier.padding(top = 3.dp),
            color = VitalColors.TextSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(5.dp))
        ProgressTrack(
            progress = visual.progress,
            color = visual.accentColor,
            height = 9.dp,
        )
    }
}

private data class ProcessVisual(
    val title: String,
    val statusText: String,
    val description: String,
    val progress: Float,
    val icon: ImageVector,
    val accentColor: Color,
    val surfaceColor: Color,
    val borderColor: Color,
)

private fun processVisual(state: AnalysisUiState): ProcessVisual = when (state.processStage) {
    AnalysisProcessStage.UPLOADING -> ProcessVisual(
        title = "采集数据上传中",
        statusText = "${state.uploadProgress}%",
        description = "正在上传采集数据，请稍候",
        progress = state.uploadProgress / 100f,
        icon = Icons.Default.CloudUpload,
        accentColor = VitalColors.Teal,
        surfaceColor = AnalysisMintSurface,
        borderColor = AnalysisMintBorder,
    )
    AnalysisProcessStage.ANALYZING -> ProcessVisual(
        title = "上传完成，分析中",
        statusText = "分析中",
        description = "服务器正在分析采集数据",
        progress = 1f,
        icon = Icons.Default.QueryStats,
        accentColor = VitalColors.Blue,
        surfaceColor = Color(0xFFF3F7FD),
        borderColor = Color(0xFFC9DCF7),
    )
    AnalysisProcessStage.COMPLETED -> ProcessVisual(
        title = "上传与分析完成",
        statusText = "100%",
        description = "服务器已返回分析结果",
        progress = 1f,
        icon = Icons.Default.Check,
        accentColor = VitalColors.Teal,
        surfaceColor = AnalysisMintSurface,
        borderColor = AnalysisMintBorder,
    )
    AnalysisProcessStage.FAILED -> ProcessVisual(
        title = "上传或分析失败",
        statusText = "失败",
        description = state.processError ?: "请检查网络后重试",
        progress = state.uploadProgress / 100f,
        icon = Icons.Default.Error,
        accentColor = VitalColors.Danger,
        surfaceColor = Color(0xFFFFF4F3),
        borderColor = Color(0xFFFFCBC7),
    )
}

@Composable
private fun SectionHeading(text: String, top: Dp) {
    Text(
        text = text,
        modifier = Modifier.padding(top = top, bottom = 13.dp),
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = VitalColors.TextPrimary,
    )
}

@Composable
private fun RowScope.MetricTrendCard(metric: AnalysisMetric, index: Int) {
    val visual = metricVisual(index)
    Column(
        modifier = Modifier
            .weight(1f)
            .height(148.dp)
            .background(Color.White, AnalysisCardShape)
            .border(1.dp, AnalysisCardBorder, AnalysisCardShape)
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "${metric.name} (${metric.unit})",
                modifier = Modifier.padding(start = 5.dp),
                fontSize = 10.sp,
                color = VitalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = metric.value,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            fontSize = 25.sp,
            fontWeight = FontWeight.Medium,
            color = VitalColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.padding(top = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (metric.increasing) "↑" else "↓",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MetricTrendColor,
            )
            Text(
                text = metric.comparison,
                modifier = Modifier.padding(start = 3.dp),
                fontSize = 10.sp,
                color = VitalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MetricTrendLine(
            values = metric.trend,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 7.dp),
        )
    }
}

@Composable
private fun MetricTrendLine(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val points = values.mapIndexed { index, value ->
            Offset(
                x = size.width * index / values.lastIndex,
                y = size.height - ((value - min) / range * size.height * 0.72f + size.height * 0.14f),
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, MetricTrendColor, style = Stroke(width = 1.8.dp.toPx()))
        points.forEach { drawCircle(MetricTrendColor, radius = 2.7.dp.toPx(), center = it) }
    }
}

private data class MetricVisual(val icon: ImageVector, val color: Color)

private fun metricVisual(index: Int): MetricVisual = when (index % 3) {
    0 -> MetricVisual(Icons.Default.Favorite, Color(0xFF00BFA1))
    1 -> MetricVisual(Icons.Default.WaterDrop, Color(0xFF439AF2))
    else -> MetricVisual(Icons.Default.Bedtime, Color(0xFF9C78E8))
}

@Composable
private fun RecordInformationCard(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, AnalysisCardShape)
            .border(1.dp, AnalysisCardBorder, AnalysisCardShape)
            .padding(horizontal = 14.dp),
    ) {
        RecordInformationRow(Icons.Default.CalendarMonth, "采集日期", state.collectionCompletedAt)
        RecordDivider()
        RecordInformationRow(Icons.Default.Sensors, "采集设备", state.deviceAddress)
        RecordDivider()
        RecordInformationRow(Icons.Outlined.Person, "采集人", state.collectorName)
    }
}

@Composable
private fun RecordInformationRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RecordIconColor,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 10.dp),
            fontSize = 14.sp,
            color = VitalColors.TextSecondary,
        )
        Text(
            text = value,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            fontSize = 14.sp,
            color = VitalColors.TextSecondary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE9EDF1)),
    )
}

@Composable
private fun AnalysisActions(
    modifier: Modifier = Modifier,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onPostQuestionnaire: () -> Unit,
    processStage: AnalysisProcessStage,
) {
    val completed = processStage == AnalysisProcessStage.COMPLETED
    val failed = processStage == AnalysisProcessStage.FAILED
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .height(57.dp)
                .clickable(enabled = completed, role = Role.Button, onClick = onHome),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = VitalColors.Teal.copy(alpha = if (completed) 1f else 0.38f),
                modifier = Modifier.size(27.dp),
            )
            Text(
                text = "回首页",
                color = VitalColors.Teal.copy(alpha = if (completed) 1f else 0.38f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        FlowButton(
            label = if (failed) "重新上传" else "填写采集后问卷",
            style = FlowButtonStyle.PRIMARY,
            modifier = Modifier.weight(1f).height(57.dp),
            onClick = if (failed) onRetry else onPostQuestionnaire,
            enabled = completed || failed,
        )
    }
}
