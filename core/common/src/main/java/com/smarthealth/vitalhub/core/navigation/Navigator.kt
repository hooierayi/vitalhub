package com.smarthealth.vitalhub.core.navigation

import com.alibaba.android.arouter.launcher.ARouter
import androidx.fragment.app.Fragment

interface FlowNavigationHost {
    fun show(fragment: Fragment, addToBackStack: Boolean = true, clearBackStack: Boolean = false)
}

/** Implemented by root-tab fragments so the app shell can restore its bottom bar after back navigation. */
interface BottomNavigationDestination {
    val bottomNavigationKey: String
}

/** Page-owned title metadata rendered by the app shell's immersive title bar. */
interface AppBarDestination {
    val appBarTitle: String
    val showAppBarBack: Boolean get() = true
    val showNotificationAction: Boolean get() = false
}

object BottomNavigationKeys {
    const val COLLECTION = "collection"
    const val RECORDS = "records"
    const val REPORTS = "reports"
    const val PROFILE = "profile"
}

object Navigator {
    fun home(host: FlowNavigationHost, clearBackStack: Boolean = true) =
        host.show(resolve(Routes.HOME), addToBackStack = false, clearBackStack = clearBackStack)

    fun questionnaire(host: FlowNavigationHost, sessionId: String, phase: String) =
        host.show(ARouter.getInstance().build(Routes.QUESTIONNAIRE)
            .withString(RouteArgs.SESSION_ID, sessionId)
            .withString(RouteArgs.QUESTIONNAIRE_PHASE, phase)
            .navigation() as Fragment)

    fun device(host: FlowNavigationHost, sessionId: String) =
        host.show(ARouter.getInstance().build(Routes.DEVICE)
            .withString(RouteArgs.SESSION_ID, sessionId)
            .navigation() as Fragment)

    fun collection(host: FlowNavigationHost, sessionId: String, mode: String) =
        host.show(ARouter.getInstance().build(Routes.COLLECTION)
            .withString(RouteArgs.SESSION_ID, sessionId)
            .withString(RouteArgs.COLLECTION_MODE, mode)
            .navigation() as Fragment)

    fun analysis(host: FlowNavigationHost, sessionId: String) =
        host.show(ARouter.getInstance().build(Routes.ANALYSIS)
            .withString(RouteArgs.SESSION_ID, sessionId)
            .navigation() as Fragment)

    private fun resolve(path: String): Fragment =
        requireNotNull(ARouter.getInstance().build(path).navigation() as? Fragment) {
            "ARouter route did not resolve to Fragment: $path"
        }
}
