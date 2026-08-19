package com.smarthealth.vitalhub.feature.analysis

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navi.RouteArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnalysisMetric(val name: String, val value: String, val unit: String, val normal: Boolean = true)
data class AnalysisUiState(
    val sessionId: String,
    val completed: Boolean = true,
    val conclusion: String = "本次记录未发现明显异常",
    val suggestion: String = "请继续保持良好生活习惯",
    val metrics: List<AnalysisMetric> = listOf(AnalysisMetric("平均心率", "72", "bpm"), AnalysisMetric("呼吸频率", "16", "次/分"), AnalysisMetric("皮温", "36.4", "℃")),
    val recordId: String = "REC20240521001",
    val duration: String = "02:36:18",
    val recordedAt: String = "2024-05-21  09:41:32",
    val deviceModel: String = "ECG-R01",
)

class AnalysisViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalysisUiState(savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty()))
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    fun openDetails() = Unit
}
