package com.smarthealth.vitalhub.feature.collection

import com.smarthealth.vitalhub.core.navi.FlowDestination
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
    fun `only preview and clip restart collection from reconnect dialog`() {
        assertTrue(FlowDestination.LIVE_PREVIEW.restartsCollectionAfterReconnect())
        assertTrue(FlowDestination.CLIP_COLLECTION.restartsCollectionAfterReconnect())

        assertFalse(FlowDestination.CONTINUOUS_RECORDING.restartsCollectionAfterReconnect())
        assertFalse(FlowDestination.DEVICE_CONNECTION.restartsCollectionAfterReconnect())
    }
}
