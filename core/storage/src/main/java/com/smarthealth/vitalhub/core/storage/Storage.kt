package com.smarthealth.vitalhub.core.storage

import android.content.Context
import com.smarthealth.vitalhub.core.storage.internal.AndroidKeyValueStorage
import com.smarthealth.vitalhub.core.storage.internal.MmkvStore
import com.smarthealth.vitalhub.core.storage.internal.SharedPreferencesStore
import com.tencent.mmkv.MMKV

/** Selects the persistence implementation used by [Storage.create]. */
enum class StorageBackend {
    SHARED_PREFERENCES,
    MMKV,
}

data class StorageOptions(
    val backend: StorageBackend = StorageBackend.MMKV,
    /** Encrypts keys and values with an application-scoped Android Keystore AES key. */
    val encrypted: Boolean = false,
    /** MMKV supports multi-process access. SharedPreferences does not guarantee it on modern Android. */
    val multiProcess: Boolean = false,
    val logger: ((message: String, throwable: Throwable?) -> Unit)? = null,
)

/** Factory for the project's local key-value stores. */
object Storage {
    @Volatile
    private var mmkvInitialized = false

    fun create(context: Context, name: String, options: StorageOptions = StorageOptions()): KVStorage {
        require(name.isNotBlank()) { "Storage name must not be blank." }
        val appContext = context.applicationContext
        val store = when (options.backend) {
            StorageBackend.SHARED_PREFERENCES -> {
                if (options.multiProcess) {
                    options.logger?.invoke(
                        "SharedPreferences does not provide reliable multi-process consistency; use MMKV instead.",
                        null,
                    )
                }
                SharedPreferencesStore(appContext, name)
            }
            StorageBackend.MMKV -> {
                ensureMmkvInitialized(appContext)
                MmkvStore(name, options.multiProcess)
            }
        }
        return AndroidKeyValueStorage(name, store, options.encrypted, options.logger)
    }

    private fun ensureMmkvInitialized(context: Context) {
        if (mmkvInitialized) return
        synchronized(this) {
            if (!mmkvInitialized) {
                MMKV.initialize(context)
                mmkvInitialized = true
            }
        }
    }
}
