package com.smarthealth.vitalhub.feature.collection

import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.foundation.device.api.DeviceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `only preview and clip restart collection from reconnect dialog`() {
        assertTrue(FlowDestination.LIVE_PREVIEW.restartsCollectionAfterReconnect())
        assertTrue(FlowDestination.CLIP_COLLECTION.restartsCollectionAfterReconnect())

        assertFalse(FlowDestination.CONTINUOUS_RECORDING.restartsCollectionAfterReconnect())
        assertFalse(FlowDestination.DEVICE_CONNECTION.restartsCollectionAfterReconnect())
    }

    @Test
    fun `entering preview from device connection starts collection as a side effect`() {
        assertEquals(
            DeviceCommand.StartCollection,
            collectionCommandForNavigation(
                FlowDestination.DEVICE_CONNECTION,
                FlowDestination.LIVE_PREVIEW,
            ),
        )
    }

    @Test
    fun `returning from preview to device connection stops collection as a side effect`() {
        assertEquals(
            DeviceCommand.StopCollection,
            collectionCommandForNavigation(
                FlowDestination.LIVE_PREVIEW,
                FlowDestination.DEVICE_CONNECTION,
            ),
        )
    }

    @Test
    fun `other collection navigation does not send preview boundary commands`() {
        assertNull(
            collectionCommandForNavigation(
                FlowDestination.LIVE_PREVIEW,
                FlowDestination.CLIP_COLLECTION,
            ),
        )
        assertNull(
            collectionCommandForNavigation(
                FlowDestination.CLIP_COLLECTION,
                FlowDestination.LIVE_PREVIEW,
            ),
        )
        assertNull(collectionCommandForNavigation(null, FlowDestination.LIVE_PREVIEW))
    }
}
