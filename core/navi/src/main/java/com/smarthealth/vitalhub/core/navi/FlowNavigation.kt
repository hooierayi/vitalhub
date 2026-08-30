package com.smarthealth.vitalhub.core.navi

import androidx.fragment.app.Fragment

enum class FlowDestination {
    HOME,
    PRE_QUESTIONNAIRE,
    DEVICE_CONNECTION,
    LIVE_PREVIEW,
    CLIP_COLLECTION,
    CONTINUOUS_RECORDING,
    POST_QUESTIONNAIRE,
}

data class FlowDestinationContext(
    val destination: FlowDestination,
    val sessionId: String? = null,
)

/** Implemented by flow pages that expose their current collection-flow destination. */
interface FlowDestinationOwner {
    val flowDestinationContext: FlowDestinationContext
}

data class FlowNavigationRequest(
    val key: String,
    val fragment: Fragment,
    val addToBackStack: Boolean = true,
    val clearBackStack: Boolean = false,
)

sealed interface FlowNavigationResult {
    data object Navigated : FlowNavigationResult
    data object Coalesced : FlowNavigationResult
    data object Busy : FlowNavigationResult
}

/** Serializes Fragment transactions without coupling the policy to an Activity. */
class FlowNavigationGate {
    private var pendingKey: String? = null

    @Synchronized
    fun begin(key: String): FlowNavigationResult {
        val pending = pendingKey
        if (pending != null) return if (pending == key) FlowNavigationResult.Coalesced else FlowNavigationResult.Busy
        pendingKey = key
        return FlowNavigationResult.Navigated
    }

    @Synchronized
    fun finish(key: String) {
        if (pendingKey == key) pendingKey = null
    }
}

interface FlowNavigationHost {
    fun show(request: FlowNavigationRequest): FlowNavigationResult
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

/** Optional text action rendered on the right side of a feature Activity title bar. */
interface AppBarActionDestination {
    val appBarActionLabel: String?
    fun onAppBarAction()
}

object BottomNavigationKeys {
    const val COLLECTION = "collection"
    const val RECORDS = "records"
    const val REPORTS = "reports"
    const val PROFILE = "profile"
}
