package com.smarthealth.vitalhub.core.storage.internal

import android.os.Parcel
import android.os.Parcelable
import com.smarthealth.vitalhub.core.storage.KVStorage

internal class AndroidKeyValueStorage(
    private val name: String,
    private val store: RawStore,
    encrypted: Boolean,
    private val logger: ((message: String, throwable: Throwable?) -> Unit)?,
) : KVStorage {
    private val lock = Any()
    private val cipher = if (encrypted) StorageCipher(name) else null

    override fun contains(key: String): Boolean = store.contains(storageKey(key))

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = ifEncrypted(
        key = key,
        defaultValue = defaultValue,
        encryptedRead = {
            when (it) {
                "true" -> true
                "false" -> false
                else -> defaultValue
            }
        },
        plainRead = { store.getBoolean(key, defaultValue) },
    )

    override fun getInt(key: String, defaultValue: Int): Int = ifEncrypted(
        key = key,
        defaultValue = defaultValue,
        encryptedRead = { it.toIntOrNull() ?: defaultValue },
        plainRead = { store.getInt(key, defaultValue) },
    )

    override fun getFloat(key: String, defaultValue: Float): Float = ifEncrypted(
        key = key,
        defaultValue = defaultValue,
        encryptedRead = { it.toFloatOrNull() ?: defaultValue },
        plainRead = { store.getFloat(key, defaultValue) },
    )

    override fun getDouble(key: String, defaultValue: Double): Double = ifEncrypted(
        key = key,
        defaultValue = defaultValue,
        encryptedRead = { it.toDoubleOrNull() ?: defaultValue },
        plainRead = { store.getString(key, null)?.toDoubleOrNull() ?: defaultValue },
    )

    override fun getLong(key: String, defaultValue: Long): Long = ifEncrypted(
        key = key,
        defaultValue = defaultValue,
        encryptedRead = { it.toLongOrNull() ?: defaultValue },
        plainRead = { store.getLong(key, defaultValue) },
    )

    override fun getString(key: String, defaultValue: String?): String? = if (cipher == null) {
        store.getString(key, defaultValue)
    } else {
        encryptedString(key) ?: defaultValue
    }

    override fun <T : Parcelable> getParcelable(key: String, clazz: Class<T>, defaultValue: T?): T? {
        val encoded = getString(key) ?: return defaultValue
        val bytes = runCatching { android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP) }.getOrElse {
            log("Unable to decode Parcelable at key '$key'.", it)
            return defaultValue
        }
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            @Suppress("UNCHECKED_CAST")
            val creator = clazz.getField("CREATOR").get(null) as? Parcelable.Creator<T>
                ?: return defaultValue
            creator.createFromParcel(parcel)
        } catch (throwable: Throwable) {
            log("Unable to read Parcelable at key '$key'.", throwable)
            defaultValue
        } finally {
            parcel.recycle()
        }
    }

    override fun edit(): KVStorage.Editor = Editor()

    private inner class Editor : KVStorage.Editor {
        private val changes = mutableListOf<(RawEditor) -> Unit>()

        override fun remove(key: String) = apply { changes += { it.remove(storageKey(key)) } }
        override fun putBoolean(key: String, value: Boolean) = apply { putValue(key, value.toString()) { editor, storedKey -> editor.putBoolean(storedKey, value) } }
        override fun putInt(key: String, value: Int) = apply { putValue(key, value.toString()) { editor, storedKey -> editor.putInt(storedKey, value) } }
        override fun putFloat(key: String, value: Float) = apply { putValue(key, value.toString()) { editor, storedKey -> editor.putFloat(storedKey, value) } }
        override fun putDouble(key: String, value: Double) = apply { putValue(key, value.toString()) { editor, storedKey -> editor.putString(storedKey, value.toString()) } }
        override fun putLong(key: String, value: Long) = apply { putValue(key, value.toString()) { editor, storedKey -> editor.putLong(storedKey, value) } }

        override fun putString(key: String, value: String?) = apply {
            if (value == null) remove(key) else putValue(key, value) { editor, storedKey -> editor.putString(storedKey, value) }
        }

        override fun putParcelable(key: String, value: Parcelable?) = apply {
            if (value == null) {
                remove(key)
            } else {
                val parcel = Parcel.obtain()
                try {
                    value.writeToParcel(parcel, 0)
                    putString(key, android.util.Base64.encodeToString(parcel.marshall(), android.util.Base64.NO_WRAP))
                } finally {
                    parcel.recycle()
                }
            }
        }

        override fun clear() = apply { changes += { it.clear() } }

        override fun commit(): Boolean = runChanges { it.commit() } ?: false
        override fun apply() {
            runChanges { editor -> editor.apply(); true }
        }

        private fun putValue(
            key: String,
            serialized: String,
            plainWrite: (RawEditor, String) -> Unit,
        ) {
            changes += { editor ->
                if (cipher == null) plainWrite(editor, key) else editor.putString(storageKey(key), cipher.encrypt(serialized))
            }
        }

        private fun runChanges(operation: (RawEditor) -> Boolean): Boolean? = runCatching {
            synchronized(lock) {
                operation(store.edit().also { editor -> changes.forEach { change -> change(editor) } })
            }
        }.onFailure { log("Unable to persist storage '$name'.", it) }.getOrNull()
    }

    private fun <T> ifEncrypted(
        key: String,
        defaultValue: T,
        encryptedRead: (String) -> T,
        plainRead: () -> T,
    ): T = if (cipher == null) {
        runCatching(plainRead).getOrElse { throwable ->
            log("Unable to read key '$key' from storage '$name'.", throwable)
            defaultValue
        }
    } else {
        encryptedString(key)?.let(encryptedRead) ?: defaultValue
    }

    private fun encryptedString(key: String): String? {
        return runCatching {
            val encrypted = store.getString(storageKey(key), null) ?: return null
            cipher?.decrypt(encrypted).also { value ->
                if (value == null) log("Unable to decrypt key '$key' from storage '$name'.", null)
            }
        }.getOrElse { throwable ->
            log("Unable to read encrypted key '$key' from storage '$name'.", throwable)
            null
        }
    }

    private fun storageKey(key: String): String = cipher?.keyFor(key) ?: key

    private fun log(message: String, throwable: Throwable?) {
        logger?.invoke(message, throwable)
    }
}
