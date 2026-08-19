package com.smarthealth.vitalhub.core.navi

import org.junit.Assert.assertEquals
import org.junit.Test

class FlowBackPolicyTest {
    @Test
    fun `clip and continuous recording return to live preview`() {
        assertEquals(FlowBackAction.PopTo(FlowDestination.LIVE_PREVIEW), FlowBackPolicy.resolve(FlowDestination.CLIP_COLLECTION))
        assertEquals(FlowBackAction.PopTo(FlowDestination.LIVE_PREVIEW), FlowBackPolicy.resolve(FlowDestination.CONTINUOUS_RECORDING))
    }

    @Test
    fun `post questionnaire returns home`() {
        assertEquals(FlowBackAction.ReturnHome, FlowBackPolicy.resolve(FlowDestination.POST_QUESTIONNAIRE))
    }

    @Test
    fun `other destinations delegate to fragment back stack`() {
        assertEquals(FlowBackAction.DelegateToBackStack, FlowBackPolicy.resolve(FlowDestination.LIVE_PREVIEW))
    }

    @Test
    fun `navigation gate coalesces duplicates and rejects competing request`() {
        val gate = FlowNavigationGate()

        assertEquals(FlowNavigationResult.Navigated, gate.begin("preview"))
        assertEquals(FlowNavigationResult.Coalesced, gate.begin("preview"))
        assertEquals(FlowNavigationResult.Busy, gate.begin("clip"))
        gate.finish("preview")
        assertEquals(FlowNavigationResult.Navigated, gate.begin("clip"))
    }
}
