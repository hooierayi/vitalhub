package com.smarthealth.vitalhub.provider.collection

import com.alibaba.android.arouter.facade.template.IProvider
import com.smarthealth.vitalhub.core.navi.FlowDestination

/** Durable checkpoint reducer for the four-step collection flow. */
interface CollectionFlowProvider : IProvider {
    fun startNewSession(): CollectionFlowSnapshot
    fun getCurrentSession(): CollectionFlowSnapshot?
    fun dispatch(sessionId: String, event: CollectionFlowEvent): CollectionFlowTransition

    /** Drops hardware-dependent readiness after application or connection interruption. */
    fun recoverInterruptedSession(): CollectionFlowSnapshot?
}

data class CollectionFlowSnapshot(
    val sessionId: String,
    val checkpoint: CollectionCheckpoint,
) {
    val completedSteps: Int get() = checkpoint.completedSteps
    val nextDestination: FlowDestination? get() = checkpoint.nextDestination
}

enum class CollectionCheckpoint(
    val completedSteps: Int,
    val nextDestination: FlowDestination?,
) {
    PRE_QUESTIONNAIRE_REQUIRED(0, FlowDestination.PRE_QUESTIONNAIRE),
    DEVICE_CONNECTION_REQUIRED(1, FlowDestination.DEVICE_CONNECTION),
    COLLECTION_REQUIRED(2, FlowDestination.LIVE_PREVIEW),
    POST_QUESTIONNAIRE_REQUIRED(3, FlowDestination.POST_QUESTIONNAIRE),
    COMPLETED(4, null),
}

sealed interface CollectionFlowEvent {
    data object PreQuestionnaireSubmitted : CollectionFlowEvent
    data object DeviceConnectionConfirmed : CollectionFlowEvent
    data object CollectionCompleted : CollectionFlowEvent
    data object PostQuestionnaireSubmitted : CollectionFlowEvent
}

sealed interface CollectionFlowTransition {
    val snapshot: CollectionFlowSnapshot?
    val nextDestination: FlowDestination?

    data class Applied(
        override val snapshot: CollectionFlowSnapshot,
        override val nextDestination: FlowDestination,
    ) : CollectionFlowTransition

    data class AlreadyApplied(
        override val snapshot: CollectionFlowSnapshot,
        override val nextDestination: FlowDestination,
    ) : CollectionFlowTransition

    data class Rejected(
        override val snapshot: CollectionFlowSnapshot?,
    ) : CollectionFlowTransition {
        override val nextDestination: FlowDestination? = null
    }
}
