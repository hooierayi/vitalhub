package com.smarthealth.vitalhub.feature.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionConfigTest {
    @Test
    fun `clock formatting adapts to configured seconds`() {
        assertEquals("00:10", formatClipClock(10L))
        assertEquals("02:00", formatClipClock(120L))
        assertEquals("01:01:01", formatClipClock(3_661L))
    }

    @Test
    fun `duration label adapts to seconds minutes and hours`() {
        assertEquals("10 秒", formatClipDurationLabel(10L))
        assertEquals("2 分钟", formatClipDurationLabel(120L))
        assertEquals("1 分钟 5 秒", formatClipDurationLabel(65L))
        assertEquals("1 小时 1 分钟 1 秒", formatClipDurationLabel(3_661L))
    }
}
