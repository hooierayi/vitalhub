package com.smarthealth.vitalhub.feature.analysis

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisProgress
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisRunner
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisFailureAction
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisTaskState
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisWaitingStatus
import com.smarthealth.vitalhub.foundation.bluetooth.BluetoothKitDevice
import com.smarthealth.vitalhub.provider.device.DeviceInfo
import com.smarthealth.vitalhub.provider.device.DeviceProvider
import com.smarthealth.vitalhub.provider.device.DeviceRecordInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `real process maps upload analysis and completed result`() = runTest(dispatcher) {
        val runner = FakeAnalysisRunner()
        val viewModel = AnalysisViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RouteArgs.SESSION_ID to "session")),
            analysisRunner = runner,
        )
        runCurrent()

        assertEquals(AnalysisTaskState.Uploading(40), viewModel.uiState.value.process)
        assertFalse(viewModel.uiState.value.canLeavePage)
        assertFalse(viewModel.uiState.value.canContinueFlow)

        advanceTimeBy(100L)
        runCurrent()
        assertEquals(
            AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
            viewModel.uiState.value.process,
        )
        assertTrue(viewModel.uiState.value.canLeavePage)
        assertTrue(viewModel.uiState.value.canContinueFlow)

        advanceUntilIdle()
        assertEquals(
            AnalysisTaskState.Completed("# 分析完成"),
            viewModel.uiState.value.process,
        )
        assertEquals("# 分析完成", viewModel.uiState.value.resultMarkdown)
        assertTrue(viewModel.uiState.value.canLeavePage)
        assertTrue(viewModel.uiState.value.canContinueFlow)
    }

    @Test
    fun `failed upload allows leaving but not continuing questionnaire`() {
        val state = AnalysisUiState(
            sessionId = "session",
            flowEntryMode = FlowEntryMode.SEQUENTIAL,
            process = AnalysisTaskState.Failed("失败", AnalysisFailureAction.NONE),
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        )

        assertTrue(state.canLeavePage)
        assertFalse(state.canContinueFlow)
    }

    @Test
    fun `failed process only enables retry for supported recovery actions`() {
        val base = AnalysisUiState(
            sessionId = "session",
            flowEntryMode = FlowEntryMode.SEQUENTIAL,
            process = AnalysisTaskState.Failed("失败", AnalysisFailureAction.NONE),
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        )

        assertFalse(base.canRetryProcess)
        assertFalse(
            base.copy(
                process = AnalysisTaskState.Failed(
                    "失败",
                    AnalysisFailureAction.RECOLLECT_DATA,
                ),
            ).canRetryProcess,
        )
        assertTrue(
            base.copy(
                process = AnalysisTaskState.Failed(
                    "失败",
                    AnalysisFailureAction.RECOLLECT_DATA,
                ),
            ).canRecollectData,
        )
        assertTrue(
            base.copy(
                process = AnalysisTaskState.Failed("失败", AnalysisFailureAction.RETRY_UPLOAD),
            ).canRetryProcess,
        )
        assertTrue(
            base.copy(
                process = AnalysisTaskState.Failed("失败", AnalysisFailureAction.RESUME_QUERY),
            ).canRetryProcess,
        )
    }

    @Test
    fun `direct collection entry remains direct through analysis`() = runTest(dispatcher) {
        val viewModel = AnalysisViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    RouteArgs.SESSION_ID to "session",
                    RouteArgs.FLOW_ENTRY_MODE to FlowEntryMode.DIRECT_RETURN_HOME,
                ),
            ),
            analysisRunner = IdleAnalysisRunner,
        )

        assertEquals(FlowEntryMode.DIRECT_RETURN_HOME, viewModel.uiState.value.flowEntryMode)
    }

    @Test
    fun `collection device displays mac address even when device has a name`() {
        val provider = FakeDeviceProvider(
            DeviceInfo(address = "AA:BB:CC:DD:EE:FF", name = "VitalHub Recorder"),
        )

        assertEquals("AA:BB:CC:DD:EE:FF", resolveDeviceAddress(provider))
    }

    @Test
    fun `collection device displays placeholder when mac address is blank`() {
        val provider = FakeDeviceProvider(DeviceInfo(address = "", name = "VitalHub Recorder"))

        assertEquals("-", resolveDeviceAddress(provider))
    }

    private class FakeAnalysisRunner : AnalysisRunner {
        override suspend fun execute(
            sessionId: String,
            action: AnalysisFailureAction?,
            onProgress: (AnalysisProgress) -> Unit,
        ) {
            onProgress(AnalysisProgress("CLIP-1", AnalysisTaskState.Uploading(40)))
            delay(100L)
            onProgress(
                AnalysisProgress(
                    "CLIP-1",
                    AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
                ),
            )
            delay(100L)
            onProgress(
                AnalysisProgress(
                    recordId = "CLIP-1",
                    state = AnalysisTaskState.Completed("# 分析完成"),
                ),
            )
        }
    }

    private object IdleAnalysisRunner : AnalysisRunner {
        override suspend fun execute(
            sessionId: String,
            action: AnalysisFailureAction?,
            onProgress: (AnalysisProgress) -> Unit,
        ) = Unit
    }

    private class FakeDeviceProvider(private val deviceInfo: DeviceInfo?) : DeviceProvider {
        override fun init(context: Context?) = Unit
        override fun getDeviceInfo(): DeviceInfo? = deviceInfo
        override fun getRecordInfo(): DeviceRecordInfo? = deviceInfo?.record
        override fun saveDevice(deviceInfo: DeviceInfo): Boolean = false
        override fun getCurrentDevice(): BluetoothKitDevice? = null
        override fun getCurrentDeviceAddress(): String? = deviceInfo?.address
        override fun getCurrentDeviceName(): String? = deviceInfo?.name
    }
}
