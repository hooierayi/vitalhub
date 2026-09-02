package com.smarthealth.vitalhub.feature.collection

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorValueFormatterTest {
    @Test
    fun `sensor values retain protocol hundredths precision`() {
        assertEquals("-9.99", (-9.99).formatSensorHundredths())
        assertEquals("25.00", 25.0.formatSensorHundredths())
        assertEquals("50.34", 50.34.formatSensorHundredths())
    }
}
