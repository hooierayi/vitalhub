package com.smarthealth.vitalhub.provider.record

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordProviderContractTest {
    @Test
    fun `saved completed records are returned newest first`() = runBlocking {
        val provider = FakeRecordProvider()
        val older = record("record-1", completedAt = 100L)
        val newer = record("record-2", completedAt = 200L)

        assertTrue(provider.saveRecord(older))
        assertTrue(provider.saveRecord(newer))

        assertEquals(listOf(newer, older), provider.getAllRecords())
    }

    @Test
    fun `analysis id is saved on the matching collection record`() = runBlocking {
        val provider = FakeRecordProvider()
        val record = record("record-1", completedAt = 100L)
        provider.saveRecord(record)

        assertTrue(provider.saveAnalysisId(record.id, "analysis-1"))

        assertEquals(
            "analysis-1",
            provider.getRecordById(record.id)?.analysisId,
        )
    }

    @Test
    fun `record id selects one record when a session contains multiple records`() = runBlocking {
        val provider = FakeRecordProvider()
        val first = record("record-1", completedAt = 100L)
        val second = record("record-2", completedAt = 200L).copy(sessionId = first.sessionId)
        provider.saveRecord(first)
        provider.saveRecord(second)

        assertEquals(first, provider.getRecordById(first.id))
        assertEquals(second, provider.getRecordById(second.id))
    }

    private fun record(id: String, completedAt: Long) = CollectionRecord(
        id = id,
        sessionId = "session-$id",
        type = RecordType.CLIP,
        recordedAtEpochMillis = completedAt,
        durationMillis = 10L,
        localFilePath = "/records/$id.vhf",
        userFingerprint = "user-fingerprint",
        deviceAddress = "AA:BB",
    )
}

private class FakeRecordProvider : RecordProvider {
    private val records = MutableStateFlow(emptyList<CollectionRecord>())

    override fun init(context: Context) = Unit

    override fun observeAllRecords(): Flow<List<CollectionRecord>> = records

    override suspend fun getAllRecords(): List<CollectionRecord> = records.value

    override suspend fun getRecordById(recordId: String): CollectionRecord? =
        records.value.firstOrNull { it.id == recordId }

    override suspend fun getRecordBySessionId(sessionId: String): CollectionRecord? =
        records.value.firstOrNull { it.sessionId == sessionId }

    override suspend fun saveRecord(record: CollectionRecord): Boolean {
        records.value = (records.value.filterNot { it.id == record.id } + record)
            .sortedByDescending(CollectionRecord::recordedAtEpochMillis)
        return true
    }

    override suspend fun saveAnalysisId(
        recordId: String,
        analysisId: String?,
    ): Boolean {
        val current = records.value.firstOrNull { it.id == recordId } ?: return false
        return saveRecord(current.copy(analysisId = analysisId))
    }
}
