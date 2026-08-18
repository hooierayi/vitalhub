package com.smarthealth.vitalhub.core.storage.internal

import android.content.Context
import android.content.SharedPreferences
import com.tencent.mmkv.MMKV

internal interface RawStore {
    fun contains(key: String): Boolean
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getInt(key: String, defaultValue: Int): Int
    fun getFloat(key: String, defaultValue: Float): Float
    fun getLong(key: String, defaultValue: Long): Long
    fun getString(key: String, defaultValue: String?): String?
    fun edit(): RawEditor
}

internal interface RawEditor {
    fun remove(key: String)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putFloat(key: String, value: Float)
    fun putLong(key: String, value: Long)
    fun putString(key: String, value: String?)
    fun clear()
    fun commit(): Boolean
    fun apply()
}

internal class SharedPreferencesStore(context: Context, name: String) : RawStore {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun contains(key: String) = preferences.contains(key)
    override fun getBoolean(key: String, defaultValue: Boolean) = preferences.getBoolean(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = preferences.getInt(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = preferences.getFloat(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = preferences.getLong(key, defaultValue)
    override fun getString(key: String, defaultValue: String?) = preferences.getString(key, defaultValue)
    override fun edit(): RawEditor = SharedPreferencesEditor(preferences.edit())

    private class SharedPreferencesEditor(private val editor: SharedPreferences.Editor) : RawEditor {
        override fun remove(key: String) = editor.remove(key).let { Unit }
        override fun putBoolean(key: String, value: Boolean) = editor.putBoolean(key, value).let { Unit }
        override fun putInt(key: String, value: Int) = editor.putInt(key, value).let { Unit }
        override fun putFloat(key: String, value: Float) = editor.putFloat(key, value).let { Unit }
        override fun putLong(key: String, value: Long) = editor.putLong(key, value).let { Unit }
        override fun putString(key: String, value: String?) = editor.putString(key, value).let { Unit }
        override fun clear() = editor.clear().let { Unit }
        override fun commit(): Boolean = editor.commit()
        override fun apply() = editor.apply()
    }
}

internal class MmkvStore(name: String, multiProcess: Boolean) : RawStore {
    private val mmkv = requireNotNull(
        MMKV.mmkvWithID(name, if (multiProcess) MMKV.MULTI_PROCESS_MODE else MMKV.SINGLE_PROCESS_MODE),
    ) { "Unable to open MMKV storage: $name" }

    override fun contains(key: String) = mmkv.containsKey(key)
    override fun getBoolean(key: String, defaultValue: Boolean) = mmkv.decodeBool(key, defaultValue)
    override fun getInt(key: String, defaultValue: Int) = mmkv.decodeInt(key, defaultValue)
    override fun getFloat(key: String, defaultValue: Float) = mmkv.decodeFloat(key, defaultValue)
    override fun getLong(key: String, defaultValue: Long) = mmkv.decodeLong(key, defaultValue)
    override fun getString(key: String, defaultValue: String?) = mmkv.decodeString(key, defaultValue)
    override fun edit(): RawEditor = MmkvEditor(mmkv)

    private class MmkvEditor(private val mmkv: MMKV) : RawEditor {
        override fun remove(key: String) = mmkv.removeValueForKey(key).let { Unit }
        override fun putBoolean(key: String, value: Boolean) = mmkv.encode(key, value).let { Unit }
        override fun putInt(key: String, value: Int) = mmkv.encode(key, value).let { Unit }
        override fun putFloat(key: String, value: Float) = mmkv.encode(key, value).let { Unit }
        override fun putLong(key: String, value: Long) = mmkv.encode(key, value).let { Unit }
        override fun putString(key: String, value: String?) = mmkv.encode(key, value).let { Unit }
        override fun clear() = mmkv.clearAll().let { Unit }
        override fun commit() = true
        override fun apply() = Unit
    }
}
