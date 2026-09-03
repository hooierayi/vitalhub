package com.smarthealth.vitalhub.feature.analysis.data

import android.content.Context
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.record.RecordType
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRepositoryTest {
    @Test
    fun `uploads once then polls until markdown result is completed`() = runTest {
        val provider = FakeRecordProvider(record())
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(
                listOf(
                    response(102, "queued"),
                    response(100, "processing"),
                    response(0, "completed", "# 健康报告"),
                ),
            ),
        )
        val repository = repository(provider, remote)
        val progress = mutableListOf<AnalysisProgress>()

        repository.execute("session-1", onProgress = progress::add)

        assertEquals(1, remote.uploadCount)
        assertEquals("A1", provider.record.analysisId)
        assertEquals(
            listOf(
                AnalysisTaskState.Uploading(0),
                AnalysisTaskState.Uploading(100),
                AnalysisTaskState.Waiting(AnalysisWaitingStatus.QUEUED),
                AnalysisTaskState.Waiting(AnalysisWaitingStatus.QUEUED),
                AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
                AnalysisTaskState.Completed("# 健康报告"),
            ),
            progress.map(AnalysisProgress::state),
        )
    }

    @Test
    fun `existing analysis id queries remote state without another upload`() = runTest {
        val provider = FakeRecordProvider(record().copy(analysisId = "A1"))
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(listOf(response(0, "completed", "done"))),
        )
        val progress = mutableListOf<AnalysisProgress>()

        repository(provider, remote).execute("session-1", onProgress = progress::add)

        assertEquals(0, remote.uploadCount)
        assertEquals(
            listOf(
                AnalysisTaskState.Waiting(AnalysisWaitingStatus.PROCESSING),
                AnalysisTaskState.Completed("done"),
            ),
            progress.map(AnalysisProgress::state),
        )
    }

    @Test
    fun `uses configured ten seconds as the fallback polling interval`() = runTest {
        val provider = FakeRecordProvider(record())
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(
                listOf(response(100, "processing"), response(0, "completed", "done")),
            ),
        )
        val delays = mutableListOf<Long>()
        val repository = DefaultAnalysisRepository(
            recordProvider = provider,
            remoteDataSource = remote,
            appVersion = "1.0",
            sleep = delays::add,
        )

        repository.execute("session-1", onProgress = {})

        assertEquals(listOf(10_000L, 10_000L), delays)
    }

    @Test
    fun `server poll interval takes priority and missing value uses app fallback`() = runTest {
        val provider = FakeRecordProvider(record())
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(
                listOf(
                    response(102, "queued", pollIntervalSecs = 7L),
                    response(100, "processing"),
                    response(0, "completed", "done"),
                ),
            ),
            uploadPollIntervalSecs = 5L,
        )
        val delays = mutableListOf<Long>()
        val repository = DefaultAnalysisRepository(
            recordProvider = provider,
            remoteDataSource = remote,
            appVersion = "1.0",
            sleep = delays::add,
        )

        repository.execute("session-1", onProgress = {})

        assertEquals(listOf(5_000L, 7_000L, 10_000L), delays)
    }

    @Test
    fun `resume query keeps analysis id and does not upload`() = runTest {
        val provider = FakeRecordProvider(record().copy(analysisId = "A1"))
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(listOf(response(0, "completed", "done"))),
        )

        repository(provider, remote).execute(
            "session-1",
            action = AnalysisFailureAction.RESUME_QUERY,
            onProgress = {},
        )

        assertEquals(0, remote.uploadCount)
        assertEquals("A1", provider.record.analysisId)
    }

    @Test
    fun `three temporary query failures preserve analysis id for manual resume`() = runTest {
        val provider = FakeRecordProvider(record().copy(analysisId = "A1"))
        val unavailable = RestResult.HttpFailure(503, 5101, "service unavailable")
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(listOf(unavailable, unavailable, unavailable)),
        )
        val progress = mutableListOf<AnalysisProgress>()

        repository(provider, remote).execute("session-1", onProgress = progress::add)

        assertEquals("A1", provider.record.analysisId)
        val failed = progress.last().state as AnalysisTaskState.Failed
        assertEquals(AnalysisFailureAction.RESUME_QUERY, failed.action)
    }

    @Test
    fun `restart analysis clears old id before uploading a new task`() = runTest {
        val provider = FakeRecordProvider(record().copy(analysisId = "OLD"))
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(listOf(response(0, "completed", "done"))),
        )

        repository(provider, remote).execute(
            "session-1",
            action = AnalysisFailureAction.RESTART_ANALYSIS,
            onProgress = {},
        )

        assertEquals(listOf<String?>(null, "A1"), provider.savedAnalysisIds)
        assertEquals(1, remote.uploadCount)
    }

    private fun repository(
        provider: FakeRecordProvider,
        remote: FakeRemoteDataSource,
    ) = DefaultAnalysisRepository(
        recordProvider = provider,
        remoteDataSource = remote,
        appVersion = "1.0",
        pollIntervalMillis = 0L,
        sleep = {},
    )

    private fun record(): CollectionRecord {
        val file = File.createTempFile("analysis", ".dcm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        return CollectionRecord(
            id = "CLIP-1",
            sessionId = "session-1",
            type = RecordType.CLIP,
            recordedAtEpochMillis = 1L,
            durationMillis = 10L,
            localFilePath = file.absolutePath,
            userFingerprint = "user",
            deviceAddress = "AA:BB",
        )
    }

    private fun response(
        code: Int,
        status: String,
        result: String? = null,
        pollIntervalSecs: Long? = null,
    ) = RestResult.Success(
        httpCode = 200,
        body = ApiEnvelope(
            code = code,
            message = status,
            data = AnalysisResultData("A1", status, result, pollIntervalSecs),
        ),
    )

    private class FakeRemoteDataSource(
        private val results: ArrayDeque<RestResult<AnalysisResultData>>,
        private val uploadPollIntervalSecs: Long? = null,
    ) : AnalysisRemoteDataSource {
        var uploadCount = 0

        override suspend fun upload(
            file: File,
            appVersion: String,
            protocolVersion: String,
            onProgress: (Int) -> Unit,
        ): RestResult<CreateAnalysisData> {
            uploadCount += 1
            onProgress(100)
            return RestResult.Success(
                httpCode = 202,
                body = ApiEnvelope(
                    102,
                    "queued",
                    CreateAnalysisData(
                        "S1",
                        "A1",
                        "queued",
                        pollIntervalSecs = uploadPollIntervalSecs,
                    ),
                ),
            )
        }

        override suspend fun getResult(analysisId: String): RestResult<AnalysisResultData> =
            results.removeFirst()
    }

    private class FakeRecordProvider(initial: CollectionRecord) : RecordProvider {
        private val records = MutableStateFlow(listOf(initial))
        var record: CollectionRecord = initial
            private set
        val savedAnalysisIds = mutableListOf<String?>()

        override fun init(context: Context) = Unit
        override fun observeAllRecords(): Flow<List<CollectionRecord>> = records
        override suspend fun getAllRecords(): List<CollectionRecord> = records.value
        override suspend fun getRecordBySessionId(sessionId: String): CollectionRecord? =
            record.takeIf { it.sessionId == sessionId }

        override suspend fun saveRecord(record: CollectionRecord): Boolean {
            this.record = record
            records.value = listOf(record)
            return true
        }

        override suspend fun saveAnalysisId(recordId: String, analysisId: String?): Boolean {
            if (record.id != recordId) return false
            savedAnalysisIds += analysisId
            return saveRecord(record.copy(analysisId = analysisId))
        }
    }
}
