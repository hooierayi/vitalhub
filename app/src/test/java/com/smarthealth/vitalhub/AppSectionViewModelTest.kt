package com.smarthealth.vitalhub

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSectionViewModelTest {
    @Test
    fun `record duration is formatted before reaching list composition`() {
        assertEquals("00:00", formatRecordDuration(-1L))
        assertEquals("01:05", formatRecordDuration(65_999L))
        assertEquals("01:01:01", formatRecordDuration(3_661_000L))
    }
}
