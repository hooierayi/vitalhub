package com.smarthealth.vitalhub.feature.collection

import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.feature.collection.shared.reconnectCommandAfter
import com.smarthealth.vitalhub.foundation.device.api.CommandResult
import com.smarthealth.vitalhub.foundation.device.api.ContinuousCollectionSubject
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionLostDialogPolicyTest {
    @Test
    fun `connection loss dialog is limited to active collection pages`() {
        assertTrue(FlowDestination.LIVE_PREVIEW.showsConnectionLostDialog())
        assertTrue(FlowDestination.CLIP_COLLECTION.showsConnectionLostDialog())
        assertTrue(FlowDestination.CONTINUOUS_RECORDING.showsConnectionLostDialog())

        assertFalse(FlowDestination.DEVICE_CONNECTION.showsConnectionLostDialog())
        assertFalse(FlowDestination.HOME.showsConnectionLostDialog())
        assertFalse(FlowDestination.PRE_QUESTIONNAIRE.showsConnectionLostDialog())
        assertFalse(FlowDestination.POST_QUESTIONNAIRE.showsConnectionLostDialog())
    }

    @Test
    fun `continuous recording reconnects without repeating its start command`() {
        val continuous = DeviceCommand.StartContinuous(
            ContinuousCollectionSubject(
                name = "测试用户",
                genderCode = 1,
                age = 30,
                year = 2026,
                month = 8,
                day = 30,
                hour = 10,
                minute = 20,
                second = 30,
            ),
        )

        assertEquals(
            null,
            reconnectCommandAfter(
                current = DeviceCommand.StartCollection,
                executed = continuous,
                result = CommandResult.Success,
            ),
        )
    }

    @Test
    fun `failed mode switch keeps the previous reconnect command`() {
        assertEquals(
            DeviceCommand.StartCollection,
            reconnectCommandAfter(
                current = DeviceCommand.StartCollection,
                executed = DeviceCommand.StartContinuous(
                    ContinuousCollectionSubject("测试用户", 1, 30, 2026, 8, 30, 10, 20, 30),
                ),
                result = CommandResult.Rejected(status = 1),
            ),
        )
    }
}
