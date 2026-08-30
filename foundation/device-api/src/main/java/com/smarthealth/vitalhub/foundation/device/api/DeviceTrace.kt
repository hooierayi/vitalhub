package com.smarthealth.vitalhub.foundation.device.api

/** Optional structured trace for observing the recorder data chain without coupling modules to Logcat. */
fun interface DeviceTrace {
    fun log(stage: String, message: String)

    companion object {
        val NONE = DeviceTrace { _, _ -> }
    }
}

fun ByteArray.traceHex(maxBytes: Int = 16): String {
    val shown = take(maxBytes.coerceAtLeast(0))
        .joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
    return if (size > maxBytes) "$shown …(+${size - maxBytes})" else shown
}
