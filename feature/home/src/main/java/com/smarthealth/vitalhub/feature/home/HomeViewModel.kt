package com.smarthealth.vitalhub.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProgress
import com.smarthealth.vitalhub.provider.collection.CollectionProgressProvider
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceSummary(val name: String, val connected: Boolean)
data class CollectionStep(
    val number: Int,
    val title: String,
    val subtitle: String,
    val completed: Boolean,
    val enabled: Boolean,
)
data class HomeUiState(
    val user: UserInfo? = null,
    val device: DeviceSummary,
    val steps: List<CollectionStep>,
    val progressError: String? = null,
) {
    val completedSteps: Int get() = steps.count { it.completed }
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            device = DeviceSummary("记录仪", connected = false),
            steps = listOf(
                CollectionStep(1, "采集前问卷", "填写基本信息与症状情况", false, true),
                CollectionStep(2, "连接记录仪", "通过蓝牙连接记录仪设备", false, false),
                CollectionStep(3, "开始采集", "选择片段或连续记录模式", false, false),
                CollectionStep(4, "采集后问卷", "填写工作与热相关症状情况", false, false),
            ),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshUser()
    }

    fun startSession(): String? = runCatching {
        requireNotNull(resolveCollectionProgressProvider()).startNewSession().sessionId
    }.getOrElse {
        reportProgressError()
        null
    }

    fun currentProgress(): CollectionFlowProgress? = runCatching {
        resolveCollectionProgressProvider()?.getCurrentProgress()
    }.getOrElse {
        reportProgressError()
        null
    }

    fun reportProgressError() {
        _uiState.value = _uiState.value.copy(progressError = "采集流程暂不可用，请稍后重试")
    }

    fun refreshUser() {
        val provider = runCatching {
            ARouter.getInstance().navigation(UserInfoProvider::class.java)
        }.getOrNull()
        val progress = runCatching {
            resolveCollectionProgressProvider()?.recoverInterruptedHardwareFlow()
        }.getOrNull()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                user = provider?.getUser(),
                steps = collectionSteps(progress?.completedSteps ?: 0),
                progressError = null,
            )
        }
    }

    private fun resolveCollectionProgressProvider(): CollectionProgressProvider? = runCatching {
        ARouter.getInstance().navigation(CollectionProgressProvider::class.java)
    }.getOrNull()

    private fun collectionSteps(completedSteps: Int): List<CollectionStep> = listOf(
        CollectionStep(1, "采集前问卷", "填写基本信息与症状情况", completedSteps >= 1, completedSteps == 0),
        CollectionStep(2, "连接记录仪", "通过蓝牙连接记录仪设备", completedSteps >= 2, completedSteps == 1),
        CollectionStep(3, "开始采集", "选择片段或连续记录模式", completedSteps >= 3, completedSteps == 2),
        CollectionStep(4, "采集后问卷", "填写工作与热相关症状情况", completedSteps >= 4, completedSteps == 3),
    )
}
