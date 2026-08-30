package com.smarthealth.vitalhub.feature.analysis

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mock process uploads then analyzes before completing`() = runTest(dispatcher) {
        val viewModel = AnalysisViewModel(SavedStateHandle(mapOf("sessionId" to "session")))
        runCurrent()

        assertEquals(AnalysisProcessStage.UPLOADING, viewModel.uiState.value.processStage)
        advanceTimeBy(800L)
        assertTrue(viewModel.uiState.value.uploadProgress in 45..55)

        advanceTimeBy(800L)
        runCurrent()
        assertEquals(AnalysisProcessStage.ANALYZING, viewModel.uiState.value.processStage)

        advanceUntilIdle()
        assertEquals(AnalysisProcessStage.COMPLETED, viewModel.uiState.value.processStage)
        assertEquals(100, viewModel.uiState.value.uploadProgress)
    }
}
