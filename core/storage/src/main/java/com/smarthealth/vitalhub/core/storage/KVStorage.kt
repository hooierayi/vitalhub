package com.smarthealth.vitalhub.core.storage

import android.os.Parcelable

/**
 * A small, backend-independent key-value store.
 *
 * Values written through an [Editor] become visible when [Editor.commit] or [Editor.apply]
 * is called. [Editor.apply] persists asynchronously, matching Android's SharedPreferences API.
 */
interface KVStorage {
    fun contains(key: String): Boolean

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun getFloat(key: String, defaultValue: Float = 0f): Float
    fun getDouble(key: String, defaultValue: Double = 0.0): Double
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun getString(key: String, defaultValue: String? = null): String?
    fun <T : Parcelable> getParcelable(key: String, clazz: Class<T>, defaultValue: T? = null): T?

    fun edit(): Editor

    interface Editor {
        fun remove(key: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putInt(key: String, value: Int): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putDouble(key: String, value: Double): Editor
        fun putLong(key: String, value: Long): Editor
        /** A null value removes the key. */
        fun putString(key: String, value: String?): Editor
        /** A null value removes the key. */
        fun putParcelable(key: String, value: Parcelable?): Editor
        fun clear(): Editor

        /** Persists the complete edit synchronously and reports whether it succeeded. */
        fun commit(): Boolean

        /** Persists the complete edit asynchronously where the selected backend supports it. */
        fun apply()
    }
}

inline fun <reified T : Parcelable> KVStorage.getParcelable(
    key: String,
    defaultValue: T? = null,
): T? = getParcelable(key, T::class.java, defaultValue)
