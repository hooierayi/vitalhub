package com.smarthealth.vitalhub.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class PatientSummary(val name: String, val gender: String, val age: Int, val patientId: String, val project: String)
data class DeviceSummary(val name: String, val connected: Boolean)
data class CollectionStep(val number: Int, val title: String, val subtitle: String, val completed: Boolean)
data class HomeUiState(
    val patient: PatientSummary,
    val device: DeviceSummary,
    val steps: List<CollectionStep>,
) {
    val completedSteps: Int get() = steps.count { it.completed }
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            PatientSummary("张三", "男", 32, "P20240521001", "心电呼吸监测项目"),
            DeviceSummary("记录仪", connected = false),
            listOf(
                CollectionStep(1, "采集前问卷", "填写基本信息与症状情况", false),
                CollectionStep(2, "连接记录仪", "通过蓝牙连接记录仪设备", false),
                CollectionStep(3, "开始采集", "选择片段或连续记录模式", false),
            ),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun startSession(): String = UUID.randomUUID().toString()
}
