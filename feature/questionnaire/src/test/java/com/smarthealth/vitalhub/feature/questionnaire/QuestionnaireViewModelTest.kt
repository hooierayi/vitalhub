package com.smarthealth.vitalhub.feature.questionnaire

import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navi.QuestionnairePhase
import com.smarthealth.vitalhub.core.navi.RouteArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionnaireViewModelTest {
    @Test
    fun nextPage_requiresEveryAnswerOnCurrentPage() {
        val viewModel = createViewModel()

        assertEquals(QuestionnaireNext.STAY, viewModel.nextPage())
        assertTrue(viewModel.uiState.value.validationError)

        viewModel.uiState.value.currentPage.forEach { viewModel.answer(it.id, it.options.first()) }
        assertEquals(QuestionnaireNext.STAY, viewModel.nextPage())
        assertEquals(1, viewModel.uiState.value.pageIndex)
    }

    @Test
    fun completedPreQuestionnaire_routesToDevice() {
        val viewModel = createViewModel()
        repeat(viewModel.uiState.value.pages.size) { page ->
            viewModel.uiState.value.currentPage.forEach { viewModel.answer(it.id, it.options.first()) }
            val result = viewModel.nextPage()
            if (page == viewModel.uiState.value.pages.lastIndex) assertEquals(QuestionnaireNext.DEVICE, result)
        }
    }

    @Test
    fun openingQuestionnaire_doesNotRestoreSavedAnswersUntilImported() {
        val store = FakeQuestionnaireAnswerStore()
        val first = createViewModel(store)
        first.answer(0, "6至7小时")

        val restored = createViewModel(store)

        assertEquals("", restored.uiState.value.answers[0])
        assertTrue(restored.hasPreviousRecord)
        restored.importPreviousAnswers()
        assertEquals("6至7小时", restored.uiState.value.answers[0])
        assertEquals("6至7小时", store.lastSaved?.answers?.get(0))
    }

    @Test
    fun questionnaireWithoutPreviousRecord_hidesImportAction() {
        val viewModel = createViewModel()

        assertEquals(false, viewModel.hasPreviousRecord)
    }

    private fun createViewModel(store: QuestionnaireAnswerStore = FakeQuestionnaireAnswerStore()) =
        QuestionnaireViewModel(
            SavedStateHandle(
                mapOf(
                    RouteArgs.SESSION_ID to "session-test",
                    RouteArgs.QUESTIONNAIRE_PHASE to QuestionnairePhase.PRE,
                ),
            ),
            store,
        )
}

private class FakeQuestionnaireAnswerStore : QuestionnaireAnswerStore {
    private var draft: QuestionnaireDraft? = null
    var lastSaved: QuestionnaireDraft? = null
        private set

    override fun load(sessionId: String, phase: String, answerCount: Int): QuestionnaireDraft? = draft

    override fun save(
        sessionId: String,
        phase: String,
        answers: List<String>,
        pageIndex: Int,
        submitted: Boolean,
    ): Boolean {
        lastSaved = QuestionnaireDraft(answers.toList(), pageIndex, submitted)
        draft = lastSaved
        return true
    }
}
