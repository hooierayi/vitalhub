package com.smarthealth.vitalhub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navigation.BottomNavigationKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSectionUiState(val key: String, val title: String, val section: String, val description: String)

class AppSectionViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val key = savedStateHandle.get<String>(AppSectionFragment.ARG_SECTION) ?: BottomNavigationKeys.RECORDS
    private val _uiState = MutableStateFlow(when (key) {
        BottomNavigationKeys.REPORTS -> AppSectionUiState(key, "健康报告", "最近报告", "采集完成后生成的 AI 分析报告将在这里展示。")
        BottomNavigationKeys.PROFILE -> AppSectionUiState(key, "我的", "个人信息", "管理受试者信息、设备帮助、隐私设置与应用信息。")
        else -> AppSectionUiState(key, "采集记录", "历史任务", "查看片段采集、连续记录、上传及分析状态。")
    })
    val uiState: StateFlow<AppSectionUiState> = _uiState.asStateFlow()
}
