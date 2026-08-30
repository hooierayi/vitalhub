package com.smarthealth.vitalhub

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        SectionTitle(state.section)
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
                InfoLine("共 ${state.records.size} 条完成记录", color = VitalColors.TextSecondary)
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(state.records, key = CollectionRecord::id) { record ->
                        CollectionRecordCard(
                            record = record,
                            userName = state.userNamesByFingerprint[record.userFingerprint],
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
    record: CollectionRecord,
    userName: String?,
) {
    InfoCard {
        Text(
            text = if (record.type == RecordType.CLIP) "片段采集" else "连续记录",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = VitalColors.TextPrimary,
        )
        StatusText("已完成", VitalColors.Success)
        KeyValueRow("记录编号", record.id)
        KeyValueRow("完成时间", formatRecordDate(record.recordedAtEpochMillis))
        KeyValueRow("采集时长", formatRecordDuration(record.durationMillis))
        KeyValueRow("受试者", userName?.takeIf(String::isNotBlank) ?: "-")
        KeyValueRow("记录仪 MAC", record.deviceAddress.takeIf(String::isNotBlank) ?: "-")
    }
}

private fun formatRecordDate(epochMillis: Long): String = SimpleDateFormat(
    "yyyy-MM-dd HH:mm:ss",
    Locale.getDefault(),
).format(Date(epochMillis))

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
