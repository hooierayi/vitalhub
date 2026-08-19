package com.smarthealth.vitalhub.feature.collection

import android.os.Parcelable
import com.smarthealth.vitalhub.core.navi.FlowDestination
import com.smarthealth.vitalhub.core.storage.KVStorage
import com.smarthealth.vitalhub.provider.collection.CollectionCheckpoint
import com.smarthealth.vitalhub.provider.collection.CollectionFlowEvent
import com.smarthealth.vitalhub.provider.collection.CollectionFlowTransition
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFlowProviderImplTest {
    @Test
    fun `four checkpoints advance to completed session`() {
        val provider = CollectionFlowProviderImpl(FakeStorage())
        val sessionId = provider.startNewSession().sessionId

        assertTransition(provider.dispatch(sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted), FlowDestination.DEVICE_CONNECTION)
        assertTransition(provider.dispatch(sessionId, CollectionFlowEvent.DeviceConnectionConfirmed), FlowDestination.LIVE_PREVIEW)
        assertTransition(provider.dispatch(sessionId, CollectionFlowEvent.CollectionCompleted), FlowDestination.POST_QUESTIONNAIRE)
        assertTransition(provider.dispatch(sessionId, CollectionFlowEvent.PostQuestionnaireSubmitted), FlowDestination.HOME)

        assertEquals(CollectionCheckpoint.COMPLETED, provider.getCurrentSession()?.checkpoint)
    }

    @Test
    fun `returning to prior checkpoint is idempotent without state regression`() {
        val provider = CollectionFlowProviderImpl(FakeStorage())
        val sessionId = provider.startNewSession().sessionId
        provider.dispatch(sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted)
        provider.dispatch(sessionId, CollectionFlowEvent.DeviceConnectionConfirmed)

        val repeated = provider.dispatch(sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted)

        assertTrue(repeated is CollectionFlowTransition.AlreadyApplied)
        assertEquals(CollectionCheckpoint.COLLECTION_REQUIRED, provider.getCurrentSession()?.checkpoint)
    }

    @Test
    fun `concurrent duplicate events apply only once`() {
        val provider = CollectionFlowProviderImpl(FakeStorage())
        val sessionId = provider.startNewSession().sessionId
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = executor.invokeAll(
                List(2) { Callable { provider.dispatch(sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted) } },
            ).map { it.get() }

            assertEquals(1, results.count { it is CollectionFlowTransition.Applied })
            assertEquals(1, results.count { it is CollectionFlowTransition.AlreadyApplied })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `interruption drops collection readiness back to device connection`() {
        val provider = CollectionFlowProviderImpl(FakeStorage())
        val sessionId = provider.startNewSession().sessionId
        provider.dispatch(sessionId, CollectionFlowEvent.PreQuestionnaireSubmitted)
        provider.dispatch(sessionId, CollectionFlowEvent.DeviceConnectionConfirmed)

        val recovered = provider.recoverInterruptedSession()

        assertEquals(CollectionCheckpoint.DEVICE_CONNECTION_REQUIRED, recovered?.checkpoint)
    }

    @Test
    fun `legacy stage migrates to matching checkpoint`() {
        val storage = FakeStorage().apply {
            putString("session-id", "legacy-session")
            putString("stage", "COLLECTION_COMPLETED")
        }
        val provider = CollectionFlowProviderImpl(storage)

        val migrated = provider.getCurrentSession()

        assertEquals(CollectionCheckpoint.POST_QUESTIONNAIRE_REQUIRED, migrated?.checkpoint)
        assertEquals(null, storage.getString("stage"))
    }

    private fun assertTransition(transition: CollectionFlowTransition, destination: FlowDestination) {
        assertTrue(transition is CollectionFlowTransition.Applied)
        assertEquals(destination, transition.nextDestination)
    }
}

private class FakeStorage : KVStorage {
    private val values = mutableMapOf<String, Any>()

    fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun contains(key: String): Boolean = synchronized(values) { key in values }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = synchronized(values) { values[key] as? Boolean ?: defaultValue }
    override fun getInt(key: String, defaultValue: Int): Int = synchronized(values) { values[key] as? Int ?: defaultValue }
    override fun getFloat(key: String, defaultValue: Float): Float = synchronized(values) { values[key] as? Float ?: defaultValue }
    override fun getDouble(key: String, defaultValue: Double): Double = synchronized(values) { values[key] as? Double ?: defaultValue }
    override fun getLong(key: String, defaultValue: Long): Long = synchronized(values) { values[key] as? Long ?: defaultValue }
    override fun getString(key: String, defaultValue: String?): String? = synchronized(values) { values[key] as? String ?: defaultValue }
    override fun <T : Parcelable> getParcelable(key: String, clazz: Class<T>, defaultValue: T?): T? = defaultValue
    override fun edit(): KVStorage.Editor = Editor(values)

    private class Editor(private val values: MutableMap<String, Any>) : KVStorage.Editor {
        override fun remove(key: String) = apply { synchronized(values) { values.remove(key) } }
        override fun putBoolean(key: String, value: Boolean) = apply { synchronized(values) { values[key] = value } }
        override fun putInt(key: String, value: Int) = apply { synchronized(values) { values[key] = value } }
        override fun putFloat(key: String, value: Float) = apply { synchronized(values) { values[key] = value } }
        override fun putDouble(key: String, value: Double) = apply { synchronized(values) { values[key] = value } }
        override fun putLong(key: String, value: Long) = apply { synchronized(values) { values[key] = value } }
        override fun putString(key: String, value: String?) = apply { synchronized(values) { if (value == null) values.remove(key) else values[key] = value } }
        override fun putParcelable(key: String, value: Parcelable?) = apply { synchronized(values) { if (value == null) values.remove(key) else values[key] = value } }
        override fun clear() = apply { synchronized(values) { values.clear() } }
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }
}
