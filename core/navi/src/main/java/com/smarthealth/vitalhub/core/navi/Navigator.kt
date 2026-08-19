package com.smarthealth.vitalhub.core.navi

import androidx.fragment.app.Fragment
import com.alibaba.android.arouter.launcher.ARouter

object Navigator {
    fun home(host: FlowNavigationHost, clearBackStack: Boolean = true): FlowNavigationResult =
        show(host, Routes.HOME, addToBackStack = false, clearBackStack = clearBackStack)

    fun editUserInfo(host: FlowNavigationHost): FlowNavigationResult =
        show(host, Routes.USER_INFO_EDIT)

    fun flow(
        host: FlowNavigationHost,
        sessionId: String,
        destination: FlowDestination,
    ): FlowNavigationResult = when (destination) {
        FlowDestination.HOME -> home(host)
        FlowDestination.PRE_QUESTIONNAIRE -> questionnaire(host, sessionId, QuestionnairePhase.PRE)
        FlowDestination.DEVICE_CONNECTION -> device(host, sessionId)
        FlowDestination.LIVE_PREVIEW -> collection(host, sessionId, CollectionMode.PREVIEW)
        FlowDestination.CLIP_COLLECTION -> collection(host, sessionId, CollectionMode.CLIP)
        FlowDestination.CONTINUOUS_RECORDING -> collection(host, sessionId, CollectionMode.CONTINUOUS)
        FlowDestination.POST_QUESTIONNAIRE -> questionnaire(host, sessionId, QuestionnairePhase.POST)
    }

    fun analysis(host: FlowNavigationHost, sessionId: String): FlowNavigationResult =
        show(host, Routes.ANALYSIS, sessionId = sessionId)

    private fun questionnaire(host: FlowNavigationHost, sessionId: String, phase: String): FlowNavigationResult =
        show(host, Routes.QUESTIONNAIRE, sessionId, phase = phase)

    private fun device(host: FlowNavigationHost, sessionId: String): FlowNavigationResult =
        show(host, Routes.DEVICE, sessionId)

    private fun collection(host: FlowNavigationHost, sessionId: String, mode: String): FlowNavigationResult =
        show(host, Routes.COLLECTION, sessionId, mode = mode)

    private fun show(
        host: FlowNavigationHost,
        path: String,
        sessionId: String? = null,
        phase: String? = null,
        mode: String? = null,
        addToBackStack: Boolean = true,
        clearBackStack: Boolean = false,
    ): FlowNavigationResult {
        val postcard = ARouter.getInstance().build(path)
        sessionId?.let { postcard.withString(RouteArgs.SESSION_ID, it) }
        phase?.let { postcard.withString(RouteArgs.QUESTIONNAIRE_PHASE, it) }
        mode?.let { postcard.withString(RouteArgs.COLLECTION_MODE, it) }
        val fragment = requireNotNull(postcard.navigation() as? Fragment) {
            "ARouter route did not resolve to Fragment: $path"
        }
        return host.show(
            FlowNavigationRequest(
                key = listOf(path, sessionId.orEmpty(), phase.orEmpty(), mode.orEmpty()).joinToString("|"),
                fragment = fragment,
                addToBackStack = addToBackStack,
                clearBackStack = clearBackStack,
            ),
        )
    }
}
