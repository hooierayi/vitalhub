package com.smarthealth.vitalhub.feature.analysis

import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.smarthealth.vitalhub.core.ui.FlowButton
import com.smarthealth.vitalhub.core.ui.FlowButtonStyle
import com.smarthealth.vitalhub.core.ui.ProgressTrack
import com.smarthealth.vitalhub.core.ui.VitalColors
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisFailureAction
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisTaskState
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisWaitingStatus
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TableAwareMovementMethod
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j

private val AnalysisCardShape = RoundedCornerShape(10.dp)
private val AnalysisCardBorder = Color(0xFFD7E1E7)
private val AnalysisMintBorder = Color(0xFFBFE4DC)
private val AnalysisMintSurface = Color(0xFFF1F9F6)
private val RecordIconColor = Color(0xFF2E8FF4)

@Composable
internal fun AnalysisScreen(
    state: AnalysisUiState,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    onRecollect: () -> Unit,
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
                    SectionHeading("分析报告", top = 24.dp)
                    AnalysisReportCard(state.resultMarkdown.orEmpty())
                }
            }
            Surface(color = Color.White, shadowElevation = 5.dp) {
                AnalysisActions(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    onHome = onHome,
                    onRetry = onRetry,
                    onRecollect = onRecollect,
                    onPostQuestionnaire = onPostQuestionnaire,
                    state = state,
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

private fun processVisual(state: AnalysisUiState): ProcessVisual = when (val process = state.process) {
    is AnalysisTaskState.Uploading -> ProcessVisual(
        title = "采集数据上传中",
        statusText = "${process.progress}%",
        description = "正在上传采集数据，请稍候",
        progress = process.progress / 100f,
        icon = Icons.Default.CloudUpload,
        accentColor = VitalColors.Teal,
        surfaceColor = AnalysisMintSurface,
        borderColor = AnalysisMintBorder,
    )
    is AnalysisTaskState.Waiting -> when (process.status) {
        AnalysisWaitingStatus.QUEUED -> ProcessVisual(
        title = "上传完成，等待分析",
        statusText = "排队中",
        description = "分析任务已进入服务器队列",
        progress = 1f,
        icon = Icons.Default.QueryStats,
        accentColor = VitalColors.Blue,
        surfaceColor = Color(0xFFF3F7FD),
        borderColor = Color(0xFFC9DCF7),
        )
        AnalysisWaitingStatus.PROCESSING -> ProcessVisual(
        title = "上传完成，分析中",
        statusText = "分析中",
        description = "服务器正在分析采集数据",
        progress = 1f,
        icon = Icons.Default.QueryStats,
        accentColor = VitalColors.Blue,
        surfaceColor = Color(0xFFF3F7FD),
        borderColor = Color(0xFFC9DCF7),
        )
        AnalysisWaitingStatus.RETRYING -> ProcessVisual(
        title = "上传完成，分析重试中",
        statusText = "重试中",
        description = process.message ?: "服务器正在重新执行分析任务",
        progress = 1f,
        icon = Icons.Default.QueryStats,
        accentColor = VitalColors.Blue,
        surfaceColor = Color(0xFFF3F7FD),
        borderColor = Color(0xFFC9DCF7),
        )
    }
    is AnalysisTaskState.Completed -> ProcessVisual(
        title = "上传与分析完成",
        statusText = "100%",
        description = "服务器已返回分析结果",
        progress = 1f,
        icon = Icons.Default.Check,
        accentColor = VitalColors.Teal,
        surfaceColor = AnalysisMintSurface,
        borderColor = AnalysisMintBorder,
    )
    is AnalysisTaskState.Failed -> ProcessVisual(
        title = "上传或分析失败",
        statusText = "失败",
        description = process.message,
        progress = 0f,
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
private fun AnalysisReportCard(markdown: String) {
    val context = LocalContext.current
    val markwon = remember(context) {
        val markdownTextSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            context.resources.displayMetrics,
        )
        val markdownImageLoader = ImageLoader.Builder(context)
            .componentRegistry {
                add(GifDecoder())
                add(SvgDecoder(context))
            }
            .build()
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(CoilImagesPlugin.create(context, markdownImageLoader))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(SoftBreakAddsNewLinePlugin.create())
            .usePlugin(MovementMethodPlugin.create(TableAwareMovementMethod.create()))
            .usePlugin(
                JLatexMathPlugin.create(markdownTextSizePx) { builder ->
                    builder.inlinesEnabled(true)
                },
            )
            .usePlugin(
                SyntaxHighlightPlugin.create(
                    Prism4j(MarkdownGrammarLocator()),
                    Prism4jThemeDefault.create(),
                ),
            )
            .build()
    }
    val content = markdown.ifBlank { "服务器未返回报告内容" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, AnalysisCardShape)
            .border(1.dp, AnalysisCardBorder, AnalysisCardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        AndroidView(
            factory = { viewContext ->
                TextView(viewContext).apply {
                    setTextColor(VitalColors.TextPrimary.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setLineSpacing(0f, 22f / 14f)
                    includeFontPadding = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            update = { textView -> markwon.setMarkdown(textView, content) },
        )
    }
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
    onRecollect: () -> Unit,
    onPostQuestionnaire: () -> Unit,
    state: AnalysisUiState,
) {
    val uploading = state.process is AnalysisTaskState.Uploading
    val failure = state.process as? AnalysisTaskState.Failed
    if (state.usesDirectHomeAction) {
        FlowButton(
            label = if (uploading) "上传中" else "回首页",
            style = FlowButtonStyle.PRIMARY,
            modifier = modifier.fillMaxWidth().height(57.dp),
            onClick = onHome,
            enabled = state.canLeavePage,
            loading = uploading,
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .height(57.dp)
                .clickable(enabled = state.canLeavePage, role = Role.Button, onClick = onHome),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = VitalColors.Teal.copy(alpha = if (state.canLeavePage) 1f else 0.38f),
                modifier = Modifier.size(27.dp),
            )
            Text(
                text = "回首页",
                color = VitalColors.Teal.copy(alpha = if (state.canLeavePage) 1f else 0.38f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        FlowButton(
            label = when {
                failure != null -> failure.action.actionLabel()
                else -> "填写采集后问卷"
            },
            style = if (failure?.action == AnalysisFailureAction.NONE) {
                FlowButtonStyle.DANGER
            } else {
                FlowButtonStyle.PRIMARY
            },
            modifier = Modifier.weight(1f).height(57.dp),
            onClick = when {
                state.canRecollectData -> onRecollect
                failure != null -> onRetry
                else -> onPostQuestionnaire
            },
            enabled = state.canOpenPostQuestionnaire ||
                state.canRetryProcess ||
                state.canRecollectData,
            loading = false,
        )
    }
}

private fun AnalysisFailureAction.actionLabel(): String = when (this) {
    AnalysisFailureAction.RETRY_UPLOAD -> "重新上传"
    AnalysisFailureAction.RESUME_QUERY -> "继续查询"
    AnalysisFailureAction.RESTART_ANALYSIS -> "重新分析"
    AnalysisFailureAction.RECOLLECT_DATA -> "重新采集"
    AnalysisFailureAction.NONE -> "服务异常"
}
