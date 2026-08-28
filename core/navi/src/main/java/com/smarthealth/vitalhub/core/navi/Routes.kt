package com.smarthealth.vitalhub.core.navi

/** Shared route contract. Feature modules depend on paths, never on each other's classes. */
object Routes {
    const val HOME = "/home/main"
    const val USER_INFO_EDIT = "/user/edit"
    const val USER_INFO_PROVIDER = "/user/service"
    const val COLLECTION_FLOW_PROVIDER = "/collection/flow/service"
    const val QUESTIONNAIRE = "/questionnaire/form"
    const val DEVICE = "/device/scan"
    const val COLLECTION = "/collection/main"
    const val ANALYSIS = "/analysis/result"
}

object RouteArgs {
    const val SESSION_ID = "sessionId"
    const val QUESTIONNAIRE_PHASE = "questionnairePhase"
    const val COLLECTION_MODE = "collectionMode"
    /** Internal navigation metadata consumed by app-level ARouter interceptors. */
    const val NAVIGATION_KEY = "__vitalhub_navigation_key"
    const val ADD_TO_BACK_STACK = "__vitalhub_add_to_back_stack"
    const val CLEAR_BACK_STACK = "__vitalhub_clear_back_stack"
}

/**
 * Application composition supplies the dynamic predicate for routes that must pass an ARouter
 * interceptor. Keeping a predicate instead of a configured path snapshot means permissions added
 * or removed after app start take effect on the next navigation.
 */
object RouteInterceptionPolicy {
    @Volatile
    private var interceptorRequired: (String) -> Boolean = { false }

    fun configure(interceptorRequired: (String) -> Boolean) {
        this.interceptorRequired = interceptorRequired
    }

    fun requiresInterception(path: String): Boolean = interceptorRequired(path)
}

object QuestionnairePhase {
    const val PRE = "pre"
    const val POST = "post"
}

object CollectionMode {
    const val PREVIEW = "preview"
    const val CLIP = "clip"
    const val CONTINUOUS = "continuous"
}
