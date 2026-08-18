package com.smarthealth.vitalhub.feature.questionnaire

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.smarthealth.vitalhub.core.navigation.QuestionnairePhase
import com.smarthealth.vitalhub.core.navigation.RouteArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MULTI_SEPARATOR = "\u001F"

data class Question(
    val id: Int,
    val number: Int,
    val title: String,
    val options: List<String>,
    val multiple: Boolean = false,
    val exclusiveOptions: Set<String> = emptySet(),
    val otherOption: String? = null,
)

data class QuestionnaireUiState(
    val sessionId: String,
    val isPre: Boolean,
    val pages: List<List<Question>>,
    val pageIndex: Int,
    val answers: List<String>,
    val validationError: Boolean = false,
) {
    val currentPage: List<Question> get() = pages[pageIndex]
    val title: String get() = if (isPre) "睡眠质量调查" else "热相关症状调查"
}

enum class QuestionnaireNext { STAY, DEVICE, ANALYSIS }

class QuestionnaireViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val isPre = savedStateHandle.get<String>(RouteArgs.QUESTIONNAIRE_PHASE) != QuestionnairePhase.POST
    private val _uiState = MutableStateFlow(
        QuestionnaireUiState(
            sessionId = savedStateHandle.get<String>(RouteArgs.SESSION_ID).orEmpty(),
            isPre = isPre,
            pages = if (isPre) prePages else postPages,
            pageIndex = 0,
            answers = List(if (isPre) 9 else 5) { "" },
        ),
    )
    val uiState: StateFlow<QuestionnaireUiState> = _uiState.asStateFlow()

    fun answer(questionId: Int, answer: String) {
        val state = _uiState.value
        _uiState.value = state.copy(
            answers = state.answers.toMutableList().also { it[questionId] = answer },
            validationError = false,
        )
    }

    fun previousPage() {
        val state = _uiState.value
        if (state.pageIndex > 0) _uiState.value = state.copy(pageIndex = state.pageIndex - 1, validationError = false)
    }

    fun nextPage(): QuestionnaireNext {
        val state = _uiState.value
        if (!state.currentPage.all { isAnswered(it, state.answers[it.id]) }) {
            _uiState.value = state.copy(validationError = true)
            return QuestionnaireNext.STAY
        }
        if (state.pageIndex < state.pages.lastIndex) {
            _uiState.value = state.copy(pageIndex = state.pageIndex + 1, validationError = false)
            return QuestionnaireNext.STAY
        }
        return if (state.isPre) QuestionnaireNext.DEVICE else QuestionnaireNext.ANALYSIS
    }

    private fun isAnswered(question: Question, answer: String): Boolean =
        answer.isNotBlank() && !(question.otherOption != null && answer.split(MULTI_SEPARATOR).any { it == question.otherOption })
}

private val prePages = listOf(
    listOf(
        Question(0, 1, "您昨晚实际睡眠大约为？", listOf("少于4小时", "4至6小时", "6至7小时", "大于7小时")),
        Question(1, 2, "您昨晚入睡所需时间（从上床到睡着）？", listOf("少于15分钟", "15至30分钟", "30至60分钟", "超过60分钟")),
        Question(2, 3, "您昨晚睡眠中是否醒来？", listOf("没有", "醒来1次", "醒来2至3次", "醒来超过3次")),
    ),
    listOf(
        Question(3, 4, "您昨晚睡眠质量如何（自我感觉）？", listOf("很好", "较好", "一般", "较差", "很差")),
        Question(4, 5, "昨晚是否因热或出汗而影响睡眠？", listOf("没有", "轻微", "中等", "严重")),
        Question(5, 6, "昨晚是否因为工作原因而影响睡眠？", listOf("没有", "轻微", "中等", "严重")),
    ),
    listOf(
        Question(6, 7, "您今早醒来后的清醒度？", listOf("非常清醒", "比较清醒", "一般", "困倦", "非常困倦")),
        Question(7, 8, "昨晚睡觉时是否使用空调或风扇？", listOf("全都没有使用", "使用了风扇", "使用了空调", "风扇和空调都用了")),
        Question(8, 9, "昨天是否有睡午觉？", listOf("没有", "少于30分钟", "30至60分钟", "大于60分钟")),
    ),
)

private val postPages = listOf(
    listOf(
        Question(0, 1, "您今天的工作时长约为？", listOf("6小时及以下", "6至8小时", "8至10小时", "10小时以上")),
        Question(1, 2, "您今天在高温时段（10:00-16:00）的工作时长约为？", listOf("1小时及以下", "1至2小时", "2至3小时", "3小时及以上")),
        Question(2, 3, "您今天在工作期间是否采取了高温防护或者降温措施？（可多选）", listOf("否", "戴防晒帽或面罩", "穿防晒衣", "戴防晒墨镜", "随身携带饮用水", "携带防暑药品（如藿香正气水）", "利用冰袋或冷毛巾进行体表降温", "利用手持便携式风扇降温", "使用车载空调制冷", "打开车窗通风"), true, setOf("否")),
    ),
    listOf(
        Question(3, 4, "您今天是否出现过热相关不适身体症状？（可多选）", listOf("头晕、乏力", "口渴、出汗过多", "胸闷、心慌", "皮肤发烫", "恶心、食欲不振", "头痛", "呼吸急促", "以上均无", "其他"), true, setOf("以上均无"), "其他"),
        Question(4, 5, "若出现上述热相关不适症状，您是否及时采取了应对措施？", listOf("未出现不适", "是", "否")),
    ),
)
