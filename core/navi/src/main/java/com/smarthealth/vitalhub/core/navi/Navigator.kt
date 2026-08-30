package com.smarthealth.vitalhub.core.navi

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.alibaba.android.arouter.facade.Postcard
import com.alibaba.android.arouter.facade.callback.NavigationCallback
import com.alibaba.android.arouter.launcher.ARouter

/** Cross-module Activity navigation plus ARouter-backed Fragment resolution for Activity-owned flows. */
object Navigator {
    fun home(host: FlowNavigationHost, clearBackStack: Boolean = true): FlowNavigationResult =
        fragment(host, Routes.HOME, key = Routes.HOME, addToBackStack = false, clearBackStack = clearBackStack)

    /** MainActivity remains below feature flows, so returning home only finishes the current Activity. */
    fun returnHome(context: Context): FlowNavigationResult {
        val activity = context as? Activity ?: return FlowNavigationResult.Busy
        activity.finish()
        return FlowNavigationResult.Navigated
    }

    fun editUserInfo(context: Context): FlowNavigationResult = activity(context, Routes.USER_INFO_EDIT)

    fun flow(
        context: Context,
        sessionId: String,
        destination: FlowDestination,
        entryMode: String = FlowEntryMode.SEQUENTIAL,
    ): FlowNavigationResult {
        val finishSourceOnArrival = context is BaseFlowActivity
        return when (destination) {
            FlowDestination.HOME -> returnHome(context)
            FlowDestination.PRE_QUESTIONNAIRE -> questionnaire(
                context,
                sessionId,
                QuestionnairePhase.PRE,
                entryMode,
                finishSourceOnArrival,
            )
            FlowDestination.POST_QUESTIONNAIRE -> questionnaire(
                context,
                sessionId,
                QuestionnairePhase.POST,
                entryMode,
                finishSourceOnArrival,
            )
            FlowDestination.DEVICE_CONNECTION,
            FlowDestination.LIVE_PREVIEW,
            FlowDestination.CLIP_COLLECTION,
            FlowDestination.CONTINUOUS_RECORDING -> collectionFlow(
                context,
                sessionId,
                destination,
                entryMode,
                finishSourceOnArrival,
            )
        }
    }

    fun collection(
        host: FlowNavigationHost,
        sessionId: String,
        destination: FlowDestination,
        addToBackStack: Boolean = true,
        entryMode: String = FlowEntryMode.SEQUENTIAL,
    ): FlowNavigationResult = when (destination) {
        FlowDestination.DEVICE_CONNECTION -> fragment(
            host,
            Routes.COLLECTION_FLOW_HOME,
            bundleOf(
                RouteArgs.SESSION_ID to sessionId,
                RouteArgs.FLOW_ENTRY_MODE to entryMode,
            ),
            key = "${Routes.COLLECTION_FLOW_HOME}|$sessionId",
            addToBackStack = addToBackStack,
        )
        FlowDestination.LIVE_PREVIEW -> collectionPage(host, sessionId, CollectionMode.PREVIEW, addToBackStack, entryMode)
        FlowDestination.CLIP_COLLECTION -> collectionPage(host, sessionId, CollectionMode.CLIP, addToBackStack, entryMode)
        FlowDestination.CONTINUOUS_RECORDING -> collectionPage(host, sessionId, CollectionMode.CONTINUOUS, addToBackStack, entryMode)
        else -> error("Destination $destination is not owned by CollectionFlowActivity.")
    }

    fun analysis(
        context: Context,
        sessionId: String,
        entryMode: String = FlowEntryMode.SEQUENTIAL,
        finishSourceOnArrival: Boolean = false,
    ): FlowNavigationResult = activity(
        context,
        Routes.ANALYSIS,
        bundleOf(
            RouteArgs.SESSION_ID to sessionId,
            RouteArgs.FLOW_ENTRY_MODE to entryMode,
        ),
        finishSourceOnArrival,
    )

    fun fragment(
        host: FlowNavigationHost,
        path: String,
        arguments: Bundle = Bundle(),
        key: String = path,
        addToBackStack: Boolean = true,
        clearBackStack: Boolean = false,
    ): FlowNavigationResult {
        val fragment = requireNotNull(
            ARouter.getInstance().build(path).with(arguments).greenChannel().navigation() as? Fragment,
        ) { "ARouter route did not resolve to Fragment: $path" }
        return host.show(
            FlowNavigationRequest(
                key = key,
                fragment = fragment,
                addToBackStack = addToBackStack,
                clearBackStack = clearBackStack,
            ),
        )
    }

    private fun questionnaire(
        context: Context,
        sessionId: String,
        phase: String,
        entryMode: String,
        finishSourceOnArrival: Boolean,
    ): FlowNavigationResult = activity(
        context,
        Routes.QUESTIONNAIRE,
        bundleOf(
            RouteArgs.SESSION_ID to sessionId,
            RouteArgs.QUESTIONNAIRE_PHASE to phase,
            RouteArgs.FLOW_ENTRY_MODE to entryMode,
        ),
        finishSourceOnArrival,
    )

    private fun collectionFlow(
        context: Context,
        sessionId: String,
        destination: FlowDestination,
        entryMode: String,
        finishSourceOnArrival: Boolean,
    ): FlowNavigationResult = activity(
        context,
        Routes.COLLECTION_FLOW,
        bundleOf(
            RouteArgs.SESSION_ID to sessionId,
            RouteArgs.FLOW_DESTINATION to destination.name,
            RouteArgs.FLOW_ENTRY_MODE to entryMode,
        ),
        finishSourceOnArrival,
    )

    private fun collectionPage(
        host: FlowNavigationHost,
        sessionId: String,
        mode: String,
        addToBackStack: Boolean,
        entryMode: String,
    ): FlowNavigationResult = fragment(
        host,
        Routes.COLLECTION,
        bundleOf(
            RouteArgs.SESSION_ID to sessionId,
            RouteArgs.COLLECTION_MODE to mode,
            RouteArgs.FLOW_ENTRY_MODE to entryMode,
        ),
        key = "${Routes.COLLECTION}|$sessionId|$mode",
        addToBackStack = addToBackStack,
    )

    private fun activity(
        context: Context,
        path: String,
        arguments: Bundle = Bundle(),
        finishSourceOnArrival: Boolean = false,
    ): FlowNavigationResult {
        val sourceActivity = (context as? Activity)?.takeIf { finishSourceOnArrival }
        val callback = sourceActivity?.let { source ->
            object : NavigationCallback {
                override fun onFound(postcard: Postcard) = Unit
                override fun onLost(postcard: Postcard) = Unit
                override fun onInterrupt(postcard: Postcard) = Unit

                override fun onArrival(postcard: Postcard) {
                    if (!source.isFinishing) source.finish()
                }
            }
        }
        ARouter.getInstance().build(path).with(arguments).navigation(context, callback)
        (context as? Activity)?.let { FlowActivityTransitions.applyAfterNavigation(it, returning = false) }
        return FlowNavigationResult.Navigated
    }

    private fun bundleOf(vararg values: Pair<String, String>): Bundle = Bundle().apply {
        values.forEach { (key, value) -> putString(key, value) }
    }
}
