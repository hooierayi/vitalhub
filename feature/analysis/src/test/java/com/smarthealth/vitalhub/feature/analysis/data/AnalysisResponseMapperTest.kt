package com.smarthealth.vitalhub.feature.analysis.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisResponseMapperTest {
    @Test
    fun `http 202 queued upload is accepted and keeps analysis id`() {
        val outcome = AnalyzeResponseMapper.map(
            uploadSuccess(httpCode = 202, code = 102, status = "queued"),
        )

        assertEquals(
            CreateAnalysisOutcome.Accepted("A1", AnalysisWaitingStatus.QUEUED),
            outcome,
        )
    }

    @Test
    fun `http 200 already received upload resumes existing task`() {
        val outcome = AnalyzeResponseMapper.map(
            uploadSuccess(httpCode = 200, code = 101, status = "processing"),
        )

        assertEquals(
            CreateAnalysisOutcome.Accepted("A1", AnalysisWaitingStatus.PROCESSING),
            outcome,
        )
    }

    @Test
    fun `successful upload without analysis id can be uploaded again`() {
        val result = RestResult.Success(
            202,
            ApiEnvelope(102, "queued", CreateAnalysisData("S1", null, "queued")),
        )

        val outcome = AnalyzeResponseMapper.map(result) as CreateAnalysisOutcome.Failed

        assertEquals(AnalysisFailureAction.RETRY_UPLOAD, outcome.problem.action)
    }

    @Test
    fun `server poll interval seconds is converted to milliseconds`() {
        val upload = AnalyzeResponseMapper.map(
            RestResult.Success(
                202,
                ApiEnvelope(
                    102,
                    "queued",
                    CreateAnalysisData("S1", "A1", "queued", pollIntervalSecs = 7L),
                ),
            ),
        ) as CreateAnalysisOutcome.Accepted
        val query = AnalysisResultResponseMapper.map(
            "A1",
            RestResult.Success(
                200,
                ApiEnvelope(
                    100,
                    "processing",
                    AnalysisResultData("A1", "processing", null, pollIntervalSecs = 9L),
                ),
            ),
        ) as QueryAnalysisOutcome.Pending

        assertEquals(7_000L, upload.pollIntervalMillis)
        assertEquals(9_000L, query.pollIntervalMillis)
    }

    @Test
    fun `unknown 2xx business code is accepted only with a known pending status`() {
        val compatible = AnalyzeResponseMapper.map(
            uploadSuccess(httpCode = 202, code = 999, status = "queued"),
        )
        val incompatible = AnalyzeResponseMapper.map(
            uploadSuccess(httpCode = 202, code = 5003, status = "queued"),
        )

        assertEquals(
            CreateAnalysisOutcome.Accepted("A1", AnalysisWaitingStatus.QUEUED),
            compatible,
        )
        assertTrue(incompatible is CreateAnalysisOutcome.Failed)
    }

    @Test
    fun `upload errors map to endpoint specific recovery actions`() {
        assertUploadAction(400, 1001, AnalysisFailureAction.NONE)
        assertUploadAction(400, 1002, AnalysisFailureAction.RECOLLECT_DATA)
        assertUploadAction(400, 1003, AnalysisFailureAction.RECOLLECT_DATA)
        assertUploadAction(400, 1004, AnalysisFailureAction.RETRY_UPLOAD)
        assertUploadAction(401, 1101, AnalysisFailureAction.NONE)
        assertUploadAction(401, 1004, AnalysisFailureAction.NONE)
        assertUploadAction(409, 1301, AnalysisFailureAction.NONE)
        assertUploadAction(503, 5101, AnalysisFailureAction.RETRY_UPLOAD)
    }

    @Test
    fun `query http 200 business codes map to completed and pending states`() {
        assertPending(100, "processing", AnalysisWaitingStatus.PROCESSING)
        assertPending(102, "queued", AnalysisWaitingStatus.QUEUED)
        assertPending(103, "retrying", AnalysisWaitingStatus.RETRYING)

        val completed = AnalysisResultResponseMapper.map(
            "A1",
            querySuccess(0, "completed", "# report"),
        )
        assertEquals(QueryAnalysisOutcome.Completed("# report"), completed)
    }

    @Test
    fun `query errors distinguish temporary terminal and restartable failures`() {
        val unavailable = AnalysisResultResponseMapper.map(
            "A1",
            RestResult.HttpFailure(503, 5101, "unavailable"),
        )
        assertTrue(unavailable is QueryAnalysisOutcome.TemporaryFailure)

        assertQueryAction(404, 1201, AnalysisFailureAction.RESTART_ANALYSIS)
        assertQueryAction(401, 1101, AnalysisFailureAction.NONE)
        assertQueryAction(403, 1102, AnalysisFailureAction.NONE)
        assertQueryAction(500, 5003, AnalysisFailureAction.RESTART_ANALYSIS)
        assertQueryAction(504, 5102, AnalysisFailureAction.RESTART_ANALYSIS)
    }

    @Test
    fun `non 2xx response cannot become upload success from business code`() {
        val outcome = AnalyzeResponseMapper.map(
            RestResult.HttpFailure(400, 100, "invalid request"),
        )

        assertTrue(outcome is CreateAnalysisOutcome.Failed)
    }

    private fun assertUploadAction(
        httpCode: Int,
        businessCode: Int,
        expected: AnalysisFailureAction,
    ) {
        val outcome = AnalyzeResponseMapper.map(
            RestResult.HttpFailure(httpCode, businessCode, null),
        ) as CreateAnalysisOutcome.Failed
        assertEquals(expected, outcome.problem.action)
    }

    private fun assertPending(
        code: Int,
        status: String,
        expected: AnalysisWaitingStatus,
    ) {
        val outcome = AnalysisResultResponseMapper.map("A1", querySuccess(code, status))
        assertEquals(QueryAnalysisOutcome.Pending(expected), outcome)
    }

    private fun assertQueryAction(
        httpCode: Int,
        businessCode: Int,
        expected: AnalysisFailureAction,
    ) {
        val outcome = AnalysisResultResponseMapper.map(
            "A1",
            RestResult.HttpFailure(httpCode, businessCode, null),
        ) as QueryAnalysisOutcome.Failed
        assertEquals(expected, outcome.problem.action)
    }

    private fun uploadSuccess(
        httpCode: Int,
        code: Int,
        status: String,
    ) = RestResult.Success(
        httpCode,
        ApiEnvelope(code, status, CreateAnalysisData("S1", "A1", status)),
    )

    private fun querySuccess(
        code: Int,
        status: String,
        result: String? = null,
    ) = RestResult.Success(
        200,
        ApiEnvelope(code, status, AnalysisResultData("A1", status, result)),
    )
}
