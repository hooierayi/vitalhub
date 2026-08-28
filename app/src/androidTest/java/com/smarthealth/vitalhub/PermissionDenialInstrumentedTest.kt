package com.smarthealth.vitalhub

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.Navigator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque

/** Device-only verification for the permission denial and app-settings fallback. */
@RunWith(AndroidJUnit4::class)
class PermissionDenialInstrumentedTest {
    @Test
    fun denied_collection_permission_opens_app_settings_guidance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Run this test with nearby-device permission revoked", collectionPermissionDenied(context))
        enableAccessibilityViewIds()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                Navigator.flow(activity, "permission-denial-session", FlowDestination.DEVICE_CONNECTION)
            }

            val denialAction = waitForNode { node ->
                isSystemDenyButton(node) || isSettingsButton(node)
            }
            val settingsButton = if (isSettingsButton(denialAction)) {
                denialAction
            } else {
                assertTrue("System permission deny button was not clickable", clickNode(denialAction))
                waitForNode(::isSettingsButton)
            }

            assertFalse("Collection Activity must not open after denial", isCollectionActivityResumed())
            assertTrue("App settings button was not clickable", clickNode(settingsButton))
            waitForRootPackage("com.android.settings")

            InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun collectionPermissionDenied(context: Context): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun enableAccessibilityViewIds() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
    }

    private fun waitForNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(UI_WAIT_ATTEMPTS) {
            automation.rootInActiveWindow?.let { root ->
                findNode(root, predicate)?.let { return it }
            }
            SystemClock.sleep(UI_WAIT_INTERVAL_MS)
        }
        error("Timed out waiting for the expected UI node")
    }

    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val pending = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (predicate(node)) return node
            repeat(node.childCount) { index -> node.getChild(index)?.let(pending::addLast) }
        }
        return null
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var clickable: AccessibilityNodeInfo? = node
        while (clickable != null && !clickable.isClickable) clickable = clickable.parent
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isSystemDenyButton(node: AccessibilityNodeInfo): Boolean =
        node.viewIdResourceName?.endsWith("permission_deny_button") == true ||
            node.text?.toString()?.lowercase() in DENY_BUTTON_LABELS

    private fun isSettingsButton(node: AccessibilityNodeInfo): Boolean = node.text?.toString() == "前往设置"

    private fun waitForRootPackage(expectedPackage: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        repeat(UI_WAIT_ATTEMPTS) {
            if (automation.rootInActiveWindow?.packageName?.toString() == expectedPackage) return
            SystemClock.sleep(UI_WAIT_INTERVAL_MS)
        }
        error("Timed out waiting for $expectedPackage")
    }

    private fun isCollectionActivityResumed(): Boolean {
        var resumed = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .any { it is com.smarthealth.vitalhub.feature.collection.CollectionFlowActivity }
        }
        return resumed
    }

    private companion object {
        val DENY_BUTTON_LABELS = setOf("don't allow", "don’t allow", "不允许", "拒绝")
        const val UI_WAIT_ATTEMPTS = 100
        const val UI_WAIT_INTERVAL_MS = 100L
    }
}
