package com.smarthealth.vitalhub

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.smarthealth.vitalhub.core.navi.CollectionMode
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.FlowDestinationOwner
import com.smarthealth.vitalhub.core.navi.FlowNavigationResult
import com.smarthealth.vitalhub.core.navi.Navigator
import com.smarthealth.vitalhub.core.navi.RouteArgs
import com.smarthealth.vitalhub.feature.collection.CollectionFlowActivity
import com.smarthealth.vitalhub.feature.analysis.AnalysisActivity
import com.smarthealth.vitalhub.feature.questionnaire.QuestionnaireActivity
import com.smarthealth.vitalhub.feature.user.UserActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Activity-owned ARouter Fragment flow, transaction gate, and back stack. */
@RunWith(AndroidJUnit4::class)
class FlowNavigationInstrumentedTest {
    @Test
    fun home_routes_to_each_feature_activity_and_arouter_fragment() {
        grantCollectionPermission()
        val sessionId = "external-route-session"

        ActivityScenario.launch(MainActivity::class.java).use {
            var main = waitForResumedActivity(MainActivity::class.java)
            assertFragment(main, R.id.main_fragment_container, "HomeFragment")

            runOnMain { Navigator.editUserInfo(main) }
            finishFeatureAndReturnHome(
                activity = waitForResumedActivity(UserActivity::class.java),
                expectedFragment = "UserInfoEditFragment",
            )

            main = waitForResumedActivity(MainActivity::class.java)
            runOnMain { Navigator.flow(main, sessionId, FlowDestination.PRE_QUESTIONNAIRE) }
            finishFeatureAndReturnHome(
                activity = waitForResumedActivity(QuestionnaireActivity::class.java),
                expectedFragment = "QuestionnaireFragment",
            )

            main = waitForResumedActivity(MainActivity::class.java)
            runOnMain { Navigator.flow(main, sessionId, FlowDestination.LIVE_PREVIEW) }
            finishFeatureAndReturnHome(
                activity = waitForResumedActivity(CollectionFlowActivity::class.java),
                expectedFragment = "CollectionFragment",
            )

            main = waitForResumedActivity(MainActivity::class.java)
            runOnMain { Navigator.analysis(main, sessionId) }
            finishFeatureAndReturnHome(
                activity = waitForResumedActivity(AnalysisActivity::class.java),
                expectedFragment = "AnalysisFragment",
            )
        }
    }

    @Test
    fun collection_activity_resolves_fragments_and_coalesces_navigation() {
        val sessionId = "instrumented-session"
        val intent = Intent(ApplicationProvider.getApplicationContext(), CollectionFlowActivity::class.java)
            .putExtra(RouteArgs.SESSION_ID, sessionId)
            .putExtra(RouteArgs.FLOW_DESTINATION, FlowDestination.LIVE_PREVIEW.name)

        ActivityScenario.launch<CollectionFlowActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val fragments = activity.supportFragmentManager
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.LIVE_PREVIEW)
                val originalFragment = fragments.findFragmentById(
                    com.smarthealth.vitalhub.core.navi.R.id.flow_fragment_container,
                )

                assertEquals(
                    FlowNavigationResult.Navigated,
                    Navigator.collection(activity, sessionId, FlowDestination.CLIP_COLLECTION),
                )
                assertEquals(
                    FlowNavigationResult.Coalesced,
                    Navigator.collection(activity, sessionId, FlowDestination.CLIP_COLLECTION),
                )
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.CLIP_COLLECTION)

                activity.onBackPressedDispatcher.onBackPressed()
                fragments.executePendingTransactions()
                assertDestination(activity, FlowDestination.LIVE_PREVIEW)
                assertSame(
                    originalFragment,
                    fragments.findFragmentById(com.smarthealth.vitalhub.core.navi.R.id.flow_fragment_container),
                )
            }
        }
    }

    private fun assertDestination(activity: CollectionFlowActivity, expected: FlowDestination) {
        val owner = activity.supportFragmentManager
            .findFragmentById(com.smarthealth.vitalhub.core.navi.R.id.flow_fragment_container) as? FlowDestinationOwner
        assertEquals(expected, owner?.flowDestinationContext?.destination)
        if (expected == FlowDestination.LIVE_PREVIEW) {
            val mode = (owner as? androidx.fragment.app.Fragment)?.arguments?.getString(RouteArgs.COLLECTION_MODE)
            assertEquals(CollectionMode.PREVIEW, mode)
        }
    }

    private fun finishFeatureAndReturnHome(activity: FragmentActivity, expectedFragment: String) {
        assertFragment(
            activity,
            com.smarthealth.vitalhub.core.navi.R.id.flow_fragment_container,
            expectedFragment,
        )
        runOnMain(activity::finish)
        waitForResumedActivity(MainActivity::class.java)
    }

    private fun assertFragment(activity: FragmentActivity, containerId: Int, expectedSimpleName: String) {
        var actualSimpleName: String? = null
        runOnMain {
            activity.supportFragmentManager.executePendingTransactions()
            actualSimpleName = activity.supportFragmentManager
                .findFragmentById(containerId)
                ?.javaClass
                ?.simpleName
        }
        assertEquals(expectedSimpleName, actualSimpleName)
    }

    private fun grantCollectionPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach { permission ->
            instrumentation.uiAutomation.grantRuntimePermission(packageName, permission)
        }
    }

    private fun <T : Activity> waitForResumedActivity(type: Class<T>): T {
        repeat(ACTIVITY_WAIT_ATTEMPTS) {
            var resumed: T? = null
            runOnMain {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull(type::isInstance)
                    ?.let(type::cast)
            }
            resumed?.let { return it }
            SystemClock.sleep(ACTIVITY_WAIT_INTERVAL_MS)
        }
        assertTrue("Timed out waiting for ${type.simpleName}", false)
        error("unreachable")
    }

    private fun runOnMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private companion object {
        const val ACTIVITY_WAIT_ATTEMPTS = 100
        const val ACTIVITY_WAIT_INTERVAL_MS = 50L
    }
}
