package com.smarthealth.vitalhub.feature.collection

/** Collection feature configuration. Durations use seconds as the minimum unit. */
internal object CollectionConfig {
    const val CLIP_DURATION_SECONDS = 10L

    init {
        require(CLIP_DURATION_SECONDS > 0L) { "Clip duration must be at least 1 second" }
    }
}

internal fun formatClipClock(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val remainingSeconds = safeSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%02d:%02d".format(minutes, remainingSeconds)
    }
}

internal fun formatClipDurationLabel(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(1L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val remainingSeconds = safeSeconds % 60L
    return buildList {
        if (hours > 0L) add("${hours} 小时")
        if (minutes > 0L) add("${minutes} 分钟")
        if (remainingSeconds > 0L) add("${remainingSeconds} 秒")
    }.joinToString(" ")
}
