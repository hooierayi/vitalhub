package com.smarthealth.vitalhub.core.navigation

/** Shared route contract. Feature modules depend on paths, never on each other's classes. */
object Routes {
    const val HOME = "/home/main"
    const val USER_INFO_EDIT = "/user/edit"
    const val USER_INFO_PROVIDER = "/user/service"
    const val COLLECTION_PROGRESS_PROVIDER = "/collection/progress/service"
    const val QUESTIONNAIRE = "/questionnaire/form"
    const val DEVICE = "/device/scan"
    const val COLLECTION = "/collection/main"
    const val ANALYSIS = "/analysis/result"
}

object RouteArgs {
    const val SESSION_ID = "sessionId"
    const val QUESTIONNAIRE_PHASE = "questionnairePhase"
    const val COLLECTION_MODE = "collectionMode"
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
