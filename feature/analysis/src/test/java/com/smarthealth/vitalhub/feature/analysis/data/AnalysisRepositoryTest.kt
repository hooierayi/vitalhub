package com.smarthealth.vitalhub.feature.analysis.data

import android.content.Context
import com.smarthealth.vitalhub.provider.record.AnalysisStatus
import com.smarthealth.vitalhub.provider.record.CollectionAnalysis
import com.smarthealth.vitalhub.provider.record.CollectionRecord
import com.smarthealth.vitalhub.provider.record.RecordProvider
import com.smarthealth.vitalhub.provider.record.RecordType
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalysisRepositoryTest {
    @Test
    fun `uploads once then polls until markdown result is completed`() = runTest {
        val file = File.createTempFile("analysis", ".dcm").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val provider = FakeRecordProvider(record(file))
        val remote = FakeRemoteDataSource(
            results = ArrayDeque(
                listOf(
                    response(102, "queued"),
                    response(100, "processing"),
                    response(0, "completed", "# 健康报告"),
                ),
            ),
        )
        val repository = DefaultAnalysisRepository(
            recordProvider = provider,
            remoteDataSource = remote,
            appVersion = "1.0",
            pollIntervalMillis = 0L,
            sleep = {},
            now = { 100L },
        )
        val progress = mutableListOf<AnalysisProgress>()

        repository.execute("session-1", onProgress = progress::add)

        assertEquals(1, remote.uploadCount)
        assertEquals(
            listOf(
                AnalysisStatus.UPLOADING,
                AnalysisStatus.UPLOADING,
                AnalysisStatus.PROCESSING,
                AnalysisStatus.QUEUED,
                AnalysisStatus.PROCESSING,
                AnalysisStatus.COMPLETED,
            ),
            progress.map(AnalysisProgress::status),
        )
        assertEquals("# 健康报告", provider.record.analysis?.resultMarkdown)
    }

    @Test
    fun `completed persisted task is restored without another upload`() = runTest {
        val file = File.createTempFile("analysis", ".dcm").apply { deleteOnExit() }
        val completed = CollectionAnalysis(
            analysisId = "A1",
            status = AnalysisStatus.COMPLETED,
            resultMarkdown = "done",
            updatedAtEpochMillis = 1L,
        )
        val provider = FakeRecordProvider(record(file).copy(analysis = completed))
        val remote = FakeRemoteDataSource(ArrayDeque())
        val repository = DefaultAnalysisRepository(
            recordProvider = provider,
            remoteDataSource = remote,
            appVersion = "1.0",
        )
        val progress = mutableListOf<AnalysisProgress>()

        repository.execute("session-1", onProgress = progress::add)

        assertEquals(0, remote.uploadCount)
        assertEquals(AnalysisStatus.COMPLETED, progress.single().status)
        assertEquals("done", progress.single().resultMarkdown)
    }

    private fun record(file: File) = CollectionRecord(
        id = "CLIP-1",
        sessionId = "session-1",
        type = RecordType.CLIP,
        recordedAtEpochMillis = 1L,
        durationMillis = 10L,
        localFilePath = file.absolutePath,
        userFingerprint = "user",
        deviceAddress = "AA:BB",
    )

    private fun response(
        code: Int,
        status: String,
        result: String? = null,
    ) = RemoteResponse(
        httpCode = 200,
        body = ApiEnvelope(
            code = code,
            message = status,
            data = AnalysisResultData("A1", status, result),
        ),
        errorCode = null,
        errorMessage = null,
    )

    private class FakeRemoteDataSource(
        private val results: ArrayDeque<RemoteResponse<AnalysisResultData>>,
    ) : AnalysisRemoteDataSource {
        var uploadCount = 0

        override suspend fun upload(
            file: File,
            appVersion: String,
            protocolVersion: String,
            onProgress: (Int) -> Unit,
        ): RemoteResponse<CreateAnalysisData> {
            uploadCount += 1
            onProgress(100)
            return RemoteResponse(
                httpCode = 202,
                body = ApiEnvelope(100, "started", CreateAnalysisData("S1", "A1", "processing")),
                errorCode = null,
                errorMessage = null,
            )
        }

        override suspend fun getResult(analysisId: String): RemoteResponse<AnalysisResultData> =
            results.removeFirst()
    }

    private class FakeRecordProvider(initial: CollectionRecord) : RecordProvider {
        private val records = MutableStateFlow(listOf(initial))
        var record: CollectionRecord = initial
            private set

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

        override suspend fun saveAnalysis(
            recordId: String,
            analysis: CollectionAnalysis,
        ): Boolean {
            if (record.id != recordId) return false
            return saveRecord(record.copy(analysis = analysis))
        }
    }
}
