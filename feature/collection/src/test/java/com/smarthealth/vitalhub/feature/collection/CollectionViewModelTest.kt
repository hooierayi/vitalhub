package com.smarthealth.vitalhub.feature.collection

import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionViewModelTest {
    @Test
    fun `clip page waits for user to start collection by default`() {
        val viewModel = CollectionViewModel(
            SavedStateHandle(
                mapOf(RouteArgs.COLLECTION_MODE to CollectionMode.CLIP),
            ),
        )

        with(viewModel.uiState.value) {
            assertFalse(isClipCollecting)
            assertEquals("00:00", clipElapsed)
            assertEquals(0f, clipProgress)
        }
    }

    @Test
    fun `continuous page waits for user to start recording by default`() {
        val viewModel = CollectionViewModel(
            SavedStateHandle(
                mapOf(RouteArgs.COLLECTION_MODE to CollectionMode.CONTINUOUS),
            ),
        )

        with(viewModel.uiState.value) {
            assertFalse(isContinuousRecording)
            assertEquals("00:00:00", recordingElapsed)
            assertEquals("", startedAt)
        }
    }

    @Test
    fun `continuous start loading prevents duplicate submission`() {
        val viewModel = CollectionViewModel(
            SavedStateHandle(
                mapOf(RouteArgs.COLLECTION_MODE to CollectionMode.CONTINUOUS),
            ),
        )

        assertTrue(viewModel.beginContinuousStart())
        assertTrue(viewModel.uiState.value.isContinuousStartLoading)
        assertFalse(viewModel.beginContinuousStart())

        viewModel.finishContinuousStart()
        assertFalse(viewModel.uiState.value.isContinuousStartLoading)
        assertTrue(viewModel.beginContinuousStart())
    }

    @Test
    fun `clearing an error does not remove a newer message`() {
        val viewModel = CollectionViewModel(SavedStateHandle())
        viewModel.reportDeviceError("new error")

        viewModel.clearFlowError("old error")
        assertEquals("new error", viewModel.uiState.value.flowError)

        viewModel.clearFlowError("new error")
        assertNull(viewModel.uiState.value.flowError)
    }

    @Test
    fun `continuous elapsed starts with time between page entry and device record`() {
        assertEquals(
            3_661_000L,
            continuousElapsedAtEntry(
                enteredAtEpochMillis = 4_661_000L,
                recordStartedAtEpochMillis = 1_000_000L,
            ),
        )
    }

    @Test
    fun `continuous elapsed does not become negative for a future record time`() {
        assertEquals(
            0L,
            continuousElapsedAtEntry(
                enteredAtEpochMillis = 1_000L,
                recordStartedAtEpochMillis = 2_000L,
            ),
        )
    }
}
