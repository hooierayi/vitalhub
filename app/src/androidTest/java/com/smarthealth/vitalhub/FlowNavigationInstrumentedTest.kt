package com.smarthealth.vitalhub

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.FlowDestinationOwner
import com.smarthealth.vitalhub.core.navi.FlowNavigationResult
import com.smarthealth.vitalhub.core.navi.Navigator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Activity, ARouter Fragment routes, transaction gate, and back policy. */
@RunWith(AndroidJUnit4::class)
class FlowNavigationInstrumentedTest {
    @Test
    fun flow_navigation_coalesces_and_applies_special_back_rules() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragments = activity.supportFragmentManager
                fragments.executePendingTransactions()
                val sessionId = "instrumented-session"

                assertEquals(
                    FlowNavigationResult.Navigated,
                    Navigator.flow(activity, sessionId, FlowDestination.LIVE_PREVIEW),
                )
                assertEquals(
                    FlowNavigationResult.Coalesced,
                    Navigator.flow(activity, sessionId, FlowDestination.LIVE_PREVIEW),
                )
                assertEquals(
                    FlowNavigationResult.Busy,
                    Navigator.flow(activity, sessionId, FlowDestination.CLIP_COLLECTION),
                )
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.LIVE_PREVIEW)

                assertEquals(
                    FlowNavigationResult.Navigated,
                    Navigator.flow(activity, sessionId, FlowDestination.CLIP_COLLECTION),
                )
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.CLIP_COLLECTION)

                activity.onBackPressedDispatcher.onBackPressed()
                assertDestination(activity, FlowDestination.LIVE_PREVIEW)

                assertEquals(
                    FlowNavigationResult.Navigated,
                    Navigator.flow(activity, sessionId, FlowDestination.POST_QUESTIONNAIRE),
                )
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.POST_QUESTIONNAIRE)

                activity.onBackPressedDispatcher.onBackPressed()
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.HOME)
            }
        }
    }

    private fun assertDestination(activity: MainActivity, expected: FlowDestination) {
        val owner = activity.supportFragmentManager
            .findFragmentById(R.id.main_fragment_container) as? FlowDestinationOwner
        assertEquals(expected, owner?.flowDestinationContext?.destination)
    }
}
