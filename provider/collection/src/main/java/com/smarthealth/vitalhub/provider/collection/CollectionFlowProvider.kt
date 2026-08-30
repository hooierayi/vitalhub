package com.smarthealth.vitalhub.provider.collection

import com.alibaba.android.arouter.facade.template.IProvider
import com.smarthealth.vitalhub.core.navi.FlowDestination

/** Durable checkpoint reducer for the three-step collection flow. */
interface CollectionFlowProvider : IProvider {
    fun startNewSession(): CollectionFlowSnapshot
    fun getCurrentSession(): CollectionFlowSnapshot?
    fun dispatch(sessionId: String, event: CollectionFlowEvent): CollectionFlowTransition

    /** Recovers the durable business checkpoint without restoring transient device state. */
    fun recoverInterruptedSession(): CollectionFlowSnapshot?
}

data class CollectionFlowSnapshot(
    val sessionId: String,
    val checkpoint: CollectionCheckpoint,
    val completedStepKeys: Set<CollectionFlowStep> = checkpoint.inferredCompletedSteps,
) {
    val completedSteps: Int get() = completedStepKeys.size
    val nextDestination: FlowDestination? get() = checkpoint.nextDestination
}

enum class CollectionFlowStep(val bit: Int) {
    PRE_QUESTIONNAIRE(1 shl 0),
    COLLECTION(1 shl 1),
    POST_QUESTIONNAIRE(1 shl 2),
}

enum class CollectionCheckpoint(
    val inferredCompletedSteps: Set<CollectionFlowStep>,
    val nextDestination: FlowDestination?,
) {
    PRE_QUESTIONNAIRE_REQUIRED(emptySet(), FlowDestination.PRE_QUESTIONNAIRE),
    COLLECTION_REQUIRED(setOf(CollectionFlowStep.PRE_QUESTIONNAIRE), FlowDestination.DEVICE_CONNECTION),
    POST_QUESTIONNAIRE_REQUIRED(
        setOf(CollectionFlowStep.PRE_QUESTIONNAIRE, CollectionFlowStep.COLLECTION),
        FlowDestination.POST_QUESTIONNAIRE,
    ),
    COMPLETED(CollectionFlowStep.entries.toSet(), null),
}

sealed interface CollectionFlowEvent {
    data object PreQuestionnaireSubmitted : CollectionFlowEvent
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
