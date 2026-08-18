package com.smarthealth.vitalhub.feature.questionnaire

import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navigation.QuestionnairePhase
import com.smarthealth.vitalhub.core.navigation.RouteArgs
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

    private fun createViewModel() = QuestionnaireViewModel(
        SavedStateHandle(mapOf(RouteArgs.SESSION_ID to "session-test", RouteArgs.QUESTIONNAIRE_PHASE to QuestionnairePhase.PRE)),
    )
}
