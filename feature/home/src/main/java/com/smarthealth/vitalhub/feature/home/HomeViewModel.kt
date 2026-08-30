package com.smarthealth.vitalhub.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowSnapshot
import com.smarthealth.vitalhub.provider.collection.CollectionCheckpoint
import com.smarthealth.vitalhub.provider.collection.CollectionFlowStep
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionStep(
    val number: Int,
    val title: String,
    val subtitle: String,
    val completed: Boolean,
    val enabled: Boolean,
)
data class HomeUiState(
    val user: UserInfo? = null,
    val steps: List<CollectionStep>,
    val checkpoint: CollectionCheckpoint? = null,
    val progressError: String? = null,
) {
    val completedSteps: Int get() = steps.count { it.completed }
    val primaryActionLabel: String
        get() = if (user == null) "填写用户信息" else "填写采集前问卷"
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            steps = listOf(
                CollectionStep(1, "采集前问卷", "填写基本信息与症状情况", false, true),
                CollectionStep(2, "数据采集", "连接记录仪并完成数据采集", false, true),
                CollectionStep(3, "采集后问卷", "填写工作与热相关症状情况", false, true),
            ),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refreshUser()
    }

    fun sessionForSequentialEntry(): CollectionFlowSnapshot? = runCatching {
        val provider = requireNotNull(resolveCollectionFlowProvider())
        provider.getCurrentSession()?.takeUnless { it.checkpoint == CollectionCheckpoint.COMPLETED }
            ?: provider.startNewSession()
    }.getOrElse {
        reportProgressError()
        null
    }

    fun sessionForDirectEntry(): CollectionFlowSnapshot? = runCatching {
        val provider = requireNotNull(resolveCollectionFlowProvider())
        provider.getCurrentSession() ?: provider.startNewSession()
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
            resolveCollectionFlowProvider()?.recoverInterruptedSession()
        }.getOrNull()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                user = provider?.getUser(),
                steps = collectionSteps(progress?.completedStepKeys.orEmpty()),
                checkpoint = progress?.checkpoint,
                progressError = null,
            )
        }
    }

    private fun resolveCollectionFlowProvider(): CollectionFlowProvider? = runCatching {
        ARouter.getInstance().navigation(CollectionFlowProvider::class.java)
    }.getOrNull()

    private fun collectionSteps(completedSteps: Set<CollectionFlowStep>): List<CollectionStep> = listOf(
        CollectionStep(
            1,
            "采集前问卷",
            "填写基本信息与症状情况",
            CollectionFlowStep.PRE_QUESTIONNAIRE in completedSteps,
            true,
        ),
        CollectionStep(
            2,
            "数据采集",
            "连接记录仪并完成数据采集",
            CollectionFlowStep.COLLECTION in completedSteps,
            true,
        ),
        CollectionStep(
            3,
            "采集后问卷",
            "填写工作与热相关症状情况",
            CollectionFlowStep.POST_QUESTIONNAIRE in completedSteps,
            true,
        ),
    )
}
