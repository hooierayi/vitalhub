package com.smarthealth.vitalhub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import com.smarthealth.vitalhub.core.ui.*
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppSectionScreen(state: AppSectionUiState) {
    FlowPage(scrollable = false, bottomBarSafe = true) {
        SectionTitle(
            text = state.section,
            trailing = if (state.key == BottomNavigationKeys.RECORDS && !state.recordsLoading) {
                "共 ${state.records.size} 条"
            } else {
                null
            },
        )
        if (state.key != BottomNavigationKeys.RECORDS) {
            InfoCard {
                InfoLine(state.description, strong = true)
                InfoLine("当前为导航模块第一版占位内容，后续接入本地任务库和服务端数据。")
            }
            return@FlowPage
        }

        when {
            state.recordsLoading -> RecordMessageCard("正在读取采集记录…")
            state.recordsError != null -> RecordMessageCard(state.recordsError, VitalColors.Danger)
            state.records.isEmpty() -> RecordMessageCard("暂无采集记录", VitalColors.TextMuted)
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(
                        items = state.records,
                        key = CollectionRecord::id,
                        contentType = CollectionRecord::type,
                    ) { record ->
                        CollectionRecordCard(
                            type = record.type,
                            recordId = record.id,
                            recordedAtEpochMillis = record.recordedAtEpochMillis,
                            durationMillis = record.durationMillis,
                            userName = state.userNamesByFingerprint[record.userFingerprint],
                            deviceAddress = record.deviceAddress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordMessageCard(message: String, color: androidx.compose.ui.graphics.Color = VitalColors.TextSecondary) {
    InfoCard {
        InfoLine(message, strong = true, color = color)
        InfoLine("完成一次片段采集或连续记录后，记录会自动显示在这里。")
    }
}

@Composable
private fun CollectionRecordCard(
    type: RecordType,
    recordId: String,
    recordedAtEpochMillis: Long,
    durationMillis: Long,
    userName: String?,
    deviceAddress: String,
) {
    val recordedAt = remember(recordedAtEpochMillis) {
        formatRecordDate(recordedAtEpochMillis)
    }
    val duration = remember(durationMillis) {
        formatRecordDuration(durationMillis)
    }
    InfoCard(
        padding = PaddingValues(18.dp),
        spacing = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordTypeIcon()
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (type == RecordType.CLIP) "片段采集" else "连续记录",
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = VitalColors.TextPrimary,
            )
            CompletedBadge()
        }

        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VitalColors.TealPale, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("记录编号", fontSize = 13.sp, color = VitalColors.TextSecondary)
            Spacer(Modifier.height(5.dp))
            Text(
                text = recordId,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = VitalColors.Teal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecordSummary(
                label = "完成时间",
                value = recordedAt,
                modifier = Modifier.weight(1.45f),
            )
            Box(
                Modifier
                    .padding(horizontal = 14.dp)
                    .width(1.dp)
                    .height(42.dp)
                    .background(VitalColors.Border),
            )
            RecordSummary(
                label = "采集时长",
                value = duration,
                modifier = Modifier.weight(.75f),
            )
        }

        Spacer(Modifier.height(16.dp))
        RecordDivider()
        RecordMetadataRow("采集人", userName?.takeIf(String::isNotBlank) ?: "-")
        RecordDivider()
        RecordMetadataRow("记录仪 MAC", deviceAddress.takeIf(String::isNotBlank) ?: "-")
    }
}

@Composable
private fun RecordTypeIcon() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(VitalColors.TealPale, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 27.dp, height = 19.dp)
                .drawWithCache {
                    val waveform = Path().apply {
                        moveTo(0f, size.height * .56f)
                        lineTo(size.width * .17f, size.height * .56f)
                        lineTo(size.width * .27f, size.height * .28f)
                        lineTo(size.width * .38f, size.height * .82f)
                        lineTo(size.width * .50f, size.height * .08f)
                        lineTo(size.width * .61f, size.height * .70f)
                        lineTo(size.width * .72f, size.height * .42f)
                        lineTo(size.width * .82f, size.height * .56f)
                        lineTo(size.width, size.height * .56f)
                    }
                    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    onDrawBehind {
                        drawPath(
                            path = waveform,
                            color = VitalColors.Teal,
                            style = stroke,
                        )
                    }
                },
        )
    }
}

@Composable
private fun CompletedBadge() {
    Text(
        text = "已完成",
        modifier = Modifier
            .border(
                width = 1.dp,
                color = VitalColors.Success,
                shape = RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = VitalColors.Success,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun RecordSummary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = VitalColors.TextSecondary)
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = VitalColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = VitalColors.TextSecondary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontSize = 14.sp,
            color = VitalColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecordDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(VitalColors.Border))
}

private val recordDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault(),
    )
}

private fun formatRecordDate(epochMillis: Long): String =
    checkNotNull(recordDateFormatter.get()).format(Date(epochMillis))

private fun formatRecordDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = totalSeconds % 3_600L / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
