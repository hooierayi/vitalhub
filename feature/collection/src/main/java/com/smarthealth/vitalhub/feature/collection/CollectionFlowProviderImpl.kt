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
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition
import java.util.UUID

/** MMKV-backed owner of the durable collection-flow checkpoints. */
@Route(path = Routes.COLLECTION_FLOW_PROVIDER)
class CollectionFlowProviderImpl() : CollectionFlowProvider {
    @Volatile
    private var storage: KVStorage? = null
    private val lock = Any()

    internal constructor(storage: KVStorage) : this() {
        this.storage = storage
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
        reduce(current, event)
    }

    override fun recoverInterruptedSession(): CollectionFlowSnapshot? = synchronized(lock) {
        val current = readCurrent() ?: return@synchronized null
        if (current.checkpoint != CollectionCheckpoint.COLLECTION_REQUIRED) return@synchronized current
        CollectionFlowSnapshot(current.sessionId, CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED).also(::write)
    }

    private fun reduce(current: CollectionFlowSnapshot, event: CollectionFlowEvent): CollectionFlowTransition = when (event) {
        CollectionFlowEvent.PreQuestionnaireSubmitted -> transition(
            current = current,
            required = CollectionCheckpoint.PRE_QUESTIONNAIRE_REQUIRED,
            next = CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED,
            destination = FlowDestination.DEVICE_CONNECTION,
            alreadyApplied = setOf(CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED, CollectionCheckpoint.COLLECTION_REQUIRED),
        )
        CollectionFlowEvent.DeviceConnectionConfirmed -> transition(
            current = current,
            required = CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED,
            next = CollectionCheckpoint.COLLECTION_REQUIRED,
            destination = FlowDestination.LIVE_PREVIEW,
            alreadyApplied = setOf(CollectionCheckpoint.COLLECTION_REQUIRED),
        )
        CollectionFlowEvent.CollectionCompleted -> transition(
            current = current,
            required = CollectionCheckpoint.COLLECTION_REQUIRED,
            next = CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED,
            destination = FlowDestination.POST_QUESTIONNAIRE,
            alreadyApplied = setOf(CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED),
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
        required -> CollectionFlowSnapshot(current.sessionId, next).also(::write)
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
        return CollectionFlowSnapshot(sessionId, checkpoint)
    }

    private fun migrateLegacyIfNeeded(currentStorage: KVStorage) {
        if (currentStorage.getInt(KEY_SCHEMA_VERSION, 0) >= SCHEMA_VERSION) return
        val sessionId = currentStorage.getString(KEY_SESSION_ID)
        val legacyStage = currentStorage.getString(LEGACY_KEY_STAGE)
        val checkpoint = legacyStage?.let(::legacyCheckpoint)
        currentStorage.edit()
            .apply {
                if (sessionId != null && checkpoint != null) putString(KEY_CHECKPOINT, checkpoint.name)
                remove(LEGACY_KEY_STAGE)
                putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            }
            .commit()
    }

    private fun legacyCheckpoint(stage: String): CollectionCheckpoint? = when (stage) {
        "PRE_QUESTIONNAIRE" -> CollectionCheckpoint.PRE_QUESTIONNAIRE_REQUIRED
        "PRE_QUESTIONNAIRE_COMPLETED" -> CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED
        "DEVICE_CONNECTED", "COLLECTION_IN_PROGRESS" -> CollectionCheckpoint.COLLECTION_REQUIRED
        "COLLECTION_COMPLETED" -> CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED
        "POST_QUESTIONNAIRE_COMPLETED" -> CollectionCheckpoint.COMPLETED
        else -> null
    }

    private fun write(snapshot: CollectionFlowSnapshot): Boolean = requireStorage().edit()
        .putString(KEY_SESSION_ID, snapshot.sessionId)
        .putString(KEY_CHECKPOINT, snapshot.checkpoint.name)
        .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
        .remove(LEGACY_KEY_STAGE)
        .commit()

    private fun requireStorage(): KVStorage = checkNotNull(storage) {
        "CollectionFlowProviderImpl must be initialized by ARouter before use."
    }

    private companion object {
        const val STORAGE_ID = "vitalhub-collection-progress"
        const val KEY_SCHEMA_VERSION = "schema-version"
        const val KEY_SESSION_ID = "session-id"
        const val KEY_CHECKPOINT = "checkpoint"
        const val LEGACY_KEY_STAGE = "stage"
        const val SCHEMA_VERSION = 2
    }
}
