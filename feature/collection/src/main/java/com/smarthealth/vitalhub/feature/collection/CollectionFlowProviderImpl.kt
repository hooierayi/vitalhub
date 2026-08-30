package com.smarthealth.vitalhub.feature.collection

import android.content.Context
import com.alibaba.android.arouter.facade.annotation.Route
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.navi.Routes
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.core.storage.Storage
import com.smarthealth.vitalhub.core.storage.StorageBackend
import com.smarthealth.vitalhub.core.storage.StorageOptions
import com.smarthealth.vitalhub.provider.collection.CollectionCheckpoint
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowProvider
import com.smarthealth.vitalhub.provider.collection.CollectionFlowSnapshot
import com.smarthealth.vitalhub.provider.collection.CollectionFlowStep
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition
import java.util.UUID

/** MMKV-backed owner of the durable collection-flow checkpoints. */
@Route(path = Routes.COLLECTION_FLOW_PROVIDER)
class CollectionFlowProviderImpl() : CollectionFlowProvider {
    @Volatile
    private var storage: KVStorage? = null
    private var clock: () -> Long = System::currentTimeMillis
    private val lock = Any()

    internal constructor(
        storage: KVStorage,
        clock: () -> Long = System::currentTimeMillis,
    ) : this() {
        this.storage = storage
        this.clock = clock
    }

    override fun init(context: Context) {
        storage = Storage.create(
            context = context.applicationContext,
            name = STORAGE_ID,
            options = StorageOptions(backend = StorageBackend.MMKV),
        )
    }

    override fun startNewSession(): CollectionFlowSnapshot = synchronized(lock) {
        CollectionFlowSnapshot(UUID.randomUUID().toString(), CollectionCheckpoint.PRE_QUESTIONNAIRE_REQUIRED)
            .also(::write)
    }

    override fun getCurrentSession(): CollectionFlowSnapshot? = synchronized(lock) {
        readCurrent()
    }

    override fun dispatch(sessionId: String, event: CollectionFlowEvent): CollectionFlowTransition = synchronized(lock) {
        val current = readCurrent()
        if (current == null || current.sessionId != sessionId) return@synchronized CollectionFlowTransition.Rejected(current)
        val recorded = current.copy(
            completedStepKeys = current.completedStepKeys + event.completedStep,
            collectionCompletedAtEpochMillis = when {
                event == CollectionFlowEvent.CollectionCompleted && current.collectionCompletedAtEpochMillis == null -> clock()
                else -> current.collectionCompletedAtEpochMillis
            },
        )
        if (recorded != current) write(recorded)
        reduce(recorded, event)
    }

    override fun recoverInterruptedSession(): CollectionFlowSnapshot? = synchronized(lock) {
        readCurrent()
    }

    private fun reduce(current: CollectionFlowSnapshot, event: CollectionFlowEvent): CollectionFlowTransition = when (event) {
        CollectionFlowEvent.PreQuestionnaireSubmitted -> transition(
            current = current,
            required = CollectionCheckpoint.PRE_QUESTIONNAIRE_REQUIRED,
            next = CollectionCheckpoint.COLLECTION_REQUIRED,
            destination = FlowDestination.DEVICE_CONNECTION,
            alreadyApplied = setOf(
                CollectionCheckpoint.COLLECTION_REQUIRED,
                CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED,
                CollectionCheckpoint.COMPLETED,
            ),
        )
        CollectionFlowEvent.CollectionCompleted -> transition(
            current = current,
            required = CollectionCheckpoint.COLLECTION_REQUIRED,
            next = CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED,
            destination = FlowDestination.POST_QUESTIONNAIRE,
            alreadyApplied = setOf(CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED, CollectionCheckpoint.COMPLETED),
        )
        CollectionFlowEvent.PostQuestionnaireSubmitted -> transition(
            current = current,
            required = CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED,
            next = CollectionCheckpoint.COMPLETED,
            destination = FlowDestination.HOME,
            alreadyApplied = setOf(CollectionCheckpoint.COMPLETED),
        )
    }

    private fun transition(
        current: CollectionFlowSnapshot,
        required: CollectionCheckpoint,
        next: CollectionCheckpoint,
        destination: FlowDestination,
        alreadyApplied: Set<CollectionCheckpoint>,
    ): CollectionFlowTransition = when (current.checkpoint) {
        required -> current.copy(checkpoint = next).also(::write)
            .let { CollectionFlowTransition.Applied(it, destination) }
        in alreadyApplied -> CollectionFlowTransition.AlreadyApplied(current, destination)
        else -> CollectionFlowTransition.Rejected(current)
    }

    private fun readCurrent(): CollectionFlowSnapshot? {
        val currentStorage = requireStorage()
        migrateLegacyIfNeeded(currentStorage)
        val sessionId = currentStorage.getString(KEY_SESSION_ID)?.takeIf(String::isNotBlank) ?: return null
        val checkpoint = currentStorage.getString(KEY_CHECKPOINT)
            ?.let { value -> CollectionCheckpoint.entries.firstOrNull { it.name == value } }
            ?: return null
        val completedStepKeys = currentStorage.getInt(KEY_COMPLETED_STEP_MASK, checkpoint.completedStepMask)
            .toCompletedStepKeys()
        val collectionCompletedAtEpochMillis = currentStorage
            .getLong(KEY_COLLECTION_COMPLETED_AT_EPOCH_MILLIS, 0L)
            .takeIf { it > 0L }
        return CollectionFlowSnapshot(
            sessionId = sessionId,
            checkpoint = checkpoint,
            completedStepKeys = completedStepKeys,
            collectionCompletedAtEpochMillis = collectionCompletedAtEpochMillis,
        )
    }

    private fun migrateLegacyIfNeeded(currentStorage: KVStorage) {
        if (currentStorage.getInt(KEY_SCHEMA_VERSION, 0) >= SCHEMA_VERSION) return
        val sessionId = currentStorage.getString(KEY_SESSION_ID)
        val legacyStage = currentStorage.getString(LEGACY_KEY_STAGE)
        val checkpoint = currentStorage.getString(KEY_CHECKPOINT)?.let(::legacyCheckpoint)
            ?: legacyStage?.let(::legacyCheckpoint)
        currentStorage.edit()
            .apply {
                if (sessionId != null && checkpoint != null) putString(KEY_CHECKPOINT, checkpoint.name)
                if (checkpoint != null) putInt(KEY_COMPLETED_STEP_MASK, checkpoint.completedStepMask)
                remove(LEGACY_KEY_STAGE)
                putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            }
            .commit()
    }

    private fun legacyCheckpoint(stage: String): CollectionCheckpoint? = when (stage) {
        "PRE_QUESTIONNAIRE", "PRE_QUESTIONNAIRE_REQUIRED" -> CollectionCheckpoint.PRE_QUESTIONNAIRE_REQUIRED
        "PRE_QUESTIONNAIRE_COMPLETED", "DEVICE_CONNECTION_REQUIRED", "DEVICE_CONNECTED",
        "COLLECTION_IN_PROGRESS", "COLLECTION_REQUIRED" ->
            CollectionCheckpoint.COLLECTION_REQUIRED
        "COLLECTION_COMPLETED", "POST_QUESTIONNAIRE_REQUIRED" -> CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED
        "POST_QUESTIONNAIRE_COMPLETED", "COMPLETED" -> CollectionCheckpoint.COMPLETED
        else -> null
    }

    private fun write(snapshot: CollectionFlowSnapshot): Boolean {
        val editor = requireStorage().edit()
            .putString(KEY_SESSION_ID, snapshot.sessionId)
            .putString(KEY_CHECKPOINT, snapshot.checkpoint.name)
            .putInt(KEY_COMPLETED_STEP_MASK, snapshot.completedStepKeys.fold(0) { mask, step -> mask or step.bit })
            .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .remove(LEGACY_KEY_STAGE)
        snapshot.collectionCompletedAtEpochMillis?.let { completedAt ->
            editor.putLong(KEY_COLLECTION_COMPLETED_AT_EPOCH_MILLIS, completedAt)
        } ?: editor.remove(KEY_COLLECTION_COMPLETED_AT_EPOCH_MILLIS)
        return editor.commit()
    }

    private val CollectionFlowEvent.completedStep: CollectionFlowStep
        get() = when (this) {
            CollectionFlowEvent.PreQuestionnaireSubmitted -> CollectionFlowStep.PRE_QUESTIONNAIRE
            CollectionFlowEvent.CollectionCompleted -> CollectionFlowStep.COLLECTION
            CollectionFlowEvent.PostQuestionnaireSubmitted -> CollectionFlowStep.POST_QUESTIONNAIRE
        }

    private val CollectionCheckpoint.completedStepMask: Int
        get() = inferredCompletedSteps.fold(0) { mask, step -> mask or step.bit }

    private fun Int.toCompletedStepKeys(): Set<CollectionFlowStep> =
        CollectionFlowStep.entries.filterTo(mutableSetOf()) { step -> this and step.bit != 0 }

    private fun requireStorage(): KVStorage = checkNotNull(storage) {
        "CollectionFlowProviderImpl must be initialized by ARouter before use."
    }

    private companion object {
        const val STORAGE_ID = "vitalhub-collection-progress"
        const val KEY_SCHEMA_VERSION = "schema-version"
        const val KEY_SESSION_ID = "session-id"
        const val KEY_CHECKPOINT = "checkpoint"
        const val KEY_COMPLETED_STEP_MASK = "completed-step-mask"
        const val KEY_COLLECTION_COMPLETED_AT_EPOCH_MILLIS = "collection-completed-at-epoch-millis"
        const val LEGACY_KEY_STAGE = "stage"
        const val SCHEMA_VERSION = 5
    }
}
