package com.smarthealth.vitalhub.feature.analysis

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navi.AnalysisEntryMode
import com.smarthealth.vitalhub.core.navi.FlowEntryMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisProgress
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisRunner
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisFailureAction
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisTaskState
import com.smarthealth.vitalhub.feature.analysis.data.AnalysisWaitingStatus
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordType
import com.smarthealth.vitalhub.provider.user.Gender
import com.smarthealth.vitalhub.provider.user.UserInfo
import com.smarthealth.vitalhub.provider.user.UserInfoProvider
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
            savedStateHandle = SavedStateHandle(
                mapOf(RouteArgs.RECORD_ID to "CLIP-1"),
            ),
            analysisRunner = runner,
            userInfoProvider = FakeUserInfoProvider,
        )
        runCurrent()

        assertEquals(AnalysisTaskState.Uploading(40), viewModel.uiState.value.process)
        assertEquals("session-1", viewModel.uiState.value.sessionId)
        assertEquals("AA:BB", viewModel.uiState.value.deviceAddress)
        assertEquals("测试用户", viewModel.uiState.value.collectorName)
        assertTrue(viewModel.uiState.value.collectionCompletedAt != "-")
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
                    RouteArgs.RECORD_ID to "CLIP-1",
                    RouteArgs.FLOW_ENTRY_MODE to FlowEntryMode.DIRECT_RETURN_HOME,
                    RouteArgs.ANALYSIS_ENTRY_MODE to AnalysisEntryMode.FROM_COLLECTION,
                ),
            ),
            analysisRunner = IdleAnalysisRunner,
        )

        assertEquals(FlowEntryMode.DIRECT_RETURN_HOME, viewModel.uiState.value.flowEntryMode)
        assertFalse(viewModel.uiState.value.usesDirectHomeAction)
        assertFalse(viewModel.uiState.value.canOpenPostQuestionnaire)
    }

    @Test
    fun `collection entry offers questionnaire after analysis is accepted`() {
        val state = AnalysisUiState(
            sessionId = "session",
            flowEntryMode = FlowEntryMode.DIRECT_RETURN_HOME,
            analysisEntryMode = AnalysisEntryMode.FROM_COLLECTION,
            process = AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        )

        assertTrue(state.canOpenPostQuestionnaire)
        assertFalse(state.usesDirectHomeAction)
    }

    @Test
    fun `record entry uses full width home after analysis is accepted`() {
        val state = AnalysisUiState(
            sessionId = "session",
            flowEntryMode = FlowEntryMode.DIRECT_RETURN_HOME,
            analysisEntryMode = AnalysisEntryMode.FROM_RECORD,
            process = AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        )

        assertFalse(state.canOpenPostQuestionnaire)
        assertTrue(state.usesDirectHomeAction)
    }

    @Test
    fun `direct entry keeps recovery action beside home after failure`() {
        val state = AnalysisUiState(
            sessionId = "session",
            flowEntryMode = FlowEntryMode.DIRECT_RETURN_HOME,
            analysisEntryMode = AnalysisEntryMode.FROM_RECORD,
            process = AnalysisTaskState.Failed(
                "失败",
                AnalysisFailureAction.RESUME_QUERY,
            ),
            collectionCompletedAt = "-",
            deviceAddress = "-",
            collectorName = "-",
        )

        assertFalse(state.usesDirectHomeAction)
        assertFalse(state.canOpenPostQuestionnaire)
        assertTrue(state.canRetryProcess)
    }

    private class FakeAnalysisRunner : AnalysisRunner {
        override suspend fun execute(
            recordId: String,
            action: AnalysisFailureAction?,
            onProgress: (AnalysisProgress) -> Unit,
        ) {
            onProgress(
                AnalysisProgress(
                    recordId = RECORD.id,
                    state = AnalysisTaskState.Uploading(40),
                    record = RECORD,
                ),
            )
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
            recordId: String,
            action: AnalysisFailureAction?,
            onProgress: (AnalysisProgress) -> Unit,
        ) = Unit
    }

    private object FakeUserInfoProvider : UserInfoProvider {
        override fun init(context: Context?) = Unit
        override fun getUser(): UserInfo = USER
        override fun getUser(fingerprint: String): UserInfo? =
            USER.takeIf { fingerprint == RECORD.userFingerprint }
        override suspend fun saveUser(user: UserInfo): Boolean = false
    }

    private companion object {
        val USER = UserInfo("测试用户", Gender.UNSPECIFIED, 30)
        val RECORD = CollectionRecord(
            id = "CLIP-1",
            sessionId = "session-1",
            type = RecordType.CLIP,
            recordedAtEpochMillis = 1_000L,
            durationMillis = 10_000L,
            localFilePath = "/records/clip-1.dcm",
            userFingerprint = USER.fingerprint,
            deviceAddress = "AA:BB",
        )
    }
}
