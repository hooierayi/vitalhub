package com.smarthealth.vitalhub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.core.navi.BottomNavigationKeys
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSectionUiState(
    val key: String,
    val title: String,
    val section: String,
    val description: String,
    val records: List<CollectionRecord> = emptyList(),
    val recordsLoading: Boolean = false,
    val recordsError: String? = null,
    val userNamesByFingerprint: Map<String, String> = emptyMap(),
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
        viewModelScope.launch {
            recordsFlow
                .catch {
                    _uiState.value = _uiState.value.copy(
                        recordsLoading = false,
                        recordsError = "记录读取失败，请稍后重试",
                    )
                }
                .collect { records ->
                    val userNames = records.mapNotNull { record ->
                        runCatching { userProvider?.getUser(record.userFingerprint)?.name }
                            .getOrNull()
                            ?.let { name -> record.userFingerprint to name }
                    }.toMap()
                    _uiState.value = _uiState.value.copy(
                        records = records,
                        recordsLoading = false,
                        recordsError = null,
                        userNamesByFingerprint = userNames,
                    )
                }
        }
    }
}
