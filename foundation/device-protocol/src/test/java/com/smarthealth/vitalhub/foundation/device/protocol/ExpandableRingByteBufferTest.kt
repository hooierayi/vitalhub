package com.smarthealth.vitalhub.foundation.device.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpandableRingByteBufferTest {
    @Test
    fun wrapsAndExpandsWithoutChangingLogicalOrder() {
        val buffer = ExpandableRingByteBuffer(initialCapacity = 4, maxCapacity = 16)
        buffer.write(byteArrayOf(1, 2, 3))
        buffer.skip(2)
        buffer.write(byteArrayOf(4, 5, 6))
        assertArrayEquals(byteArrayOf(3, 4, 5, 6), buffer.copy(0, buffer.size))

        buffer.write(byteArrayOf(7, 8, 9))
        assertEquals(7, buffer.size)
        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7, 8, 9), buffer.copy(0, buffer.size))
    }
}
