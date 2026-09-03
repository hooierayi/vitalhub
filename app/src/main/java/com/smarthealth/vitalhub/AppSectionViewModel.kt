package com.smarthealth.vitalhub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.record.RecordType
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class AppSectionUiState(
    val key: String,
    val title: String,
    val section: String,
    val description: String,
    val records: List<RecordListItemUi> = emptyList(),
    val recordsLoading: Boolean = false,
    val recordsError: String? = null,
)

@Immutable
data class RecordListItemUi(
    val id: String,
    val type: RecordType,
    val recordedAt: String,
    val duration: String,
    val userName: String,
    val deviceAddress: String,
)

class AppSectionViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val key = savedStateHandle.get<String>(AppSectionFragment.ARG_SECTION) ?: BottomNavigationKeys.RECORDS
    private val _uiState = MutableStateFlow(when (key) {
        BottomNavigationKeys.REPORTS -> AppSectionUiState(key, "健康报告", "最近报告", "采集完成后生成的 AI 分析报告将在这里展示。")
        BottomNavigationKeys.PROFILE -> AppSectionUiState(key, "我的", "个人信息", "管理受试者信息、设备帮助、隐私设置与应用信息。")
        else -> AppSectionUiState(
            key,
            "采集记录",
            "历史记录",
            "查看已完成的片段采集和连续记录。",
            recordsLoading = true,
        )
    })
    val uiState: StateFlow<AppSectionUiState> = _uiState.asStateFlow()

    init {
        if (key == BottomNavigationKeys.RECORDS) observeRecords()
    }

    private fun observeRecords() {
        val userProvider = runCatching {
            ARouter.getInstance().navigation(UserInfoProvider::class.java)
        }.getOrNull()
        val recordsFlow = runCatching {
            ARouter.getInstance().navigation(RecordProvider::class.java)?.observeAllRecords()
        }.getOrNull()
        if (recordsFlow == null) {
            _uiState.value = _uiState.value.copy(
                recordsLoading = false,
                recordsError = "记录服务暂不可用",
            )
            return
        }
        val userNameCache = mutableMapOf<String, String>()
        viewModelScope.launch {
            recordsFlow
                .map { records ->
                    withContext(Dispatchers.IO) {
                        val userNames = records.asSequence()
                            .map(CollectionRecord::userFingerprint)
                            .filter(String::isNotBlank)
                            .distinct()
                            .associateWith { fingerprint ->
                                userNameCache.getOrPut(fingerprint) {
                                    runCatching { userProvider?.getUser(fingerprint)?.name }
                                        .getOrNull()
                                        ?.takeIf(String::isNotBlank)
                                        ?: "-"
                                }
                            }
                        records.map { record ->
                            RecordListItemUi(
                                id = record.id,
                                type = record.type,
                                recordedAt = formatRecordDate(record.recordedAtEpochMillis),
                                duration = formatRecordDuration(record.durationMillis),
                                userName = userNames[record.userFingerprint] ?: "-",
                                deviceAddress = record.deviceAddress.takeIf(String::isNotBlank) ?: "-",
                            )
                        }
                    }
                }
                .catch {
                    _uiState.value = _uiState.value.copy(
                        recordsLoading = false,
                        recordsError = "记录读取失败，请稍后重试",
                    )
                }
                .collect { records ->
                    _uiState.value = _uiState.value.copy(
                        records = records,
                        recordsLoading = false,
                        recordsError = null,
                    )
                }
        }
    }
}

private val recordDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.getDefault(),
    )
}

private fun formatRecordDate(epochMillis: Long): String =
    checkNotNull(recordDateFormatter.get()).format(Date(epochMillis))

internal fun formatRecordDuration(durationMillis: Long): String {
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
