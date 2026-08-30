package com.smarthealth.vitalhub.provider.device

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceInfo(
    val address: String,
    val name: String? = null,
    val record: DeviceRecordInfo? = null,
) : Parcelable

/** One device-internal card-writing operation. The id is assigned by the App for tracking. */
@Parcelize
data class DeviceRecordInfo(
    val id: String,
    val startedAtEpochMillis: Long,
) : Parcelable
