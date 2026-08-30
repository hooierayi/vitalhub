package com.smarthealth.vitalhub.feature.collection

import androidx.lifecycle.SavedStateHandle
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.provider.record.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CollectionRecordViewModelTest {
    @Test
    fun `completed clip record keeps its session user device and file associations`() {
        val viewModel = CollectionViewModel(
            SavedStateHandle(
                mapOf(
                    RouteArgs.COLLECTION_MODE to CollectionMode.CLIP,
                    RouteArgs.SESSION_ID to "session-1",
                    "clipIsCollecting" to false,
                ),
            ),
        )
        viewModel.markLocalRecordingStarted("/records/clip.vhf")

        val record = viewModel.completedRecord(
            userFingerprint = "user-fingerprint",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
        )

        assertNotNull(record)
        assertEquals("session-1", record?.sessionId)
        assertEquals(RecordType.CLIP, record?.type)
        assertEquals("/records/clip.vhf", record?.localFilePath)
        assertEquals("user-fingerprint", record?.userFingerprint)
        assertEquals("AA:BB:CC:DD:EE:FF", record?.deviceAddress)
    }
}
