package com.smarthealth.vitalhub.provider.user

import java.security.MessageDigest

/**
 * The basic information for the participant currently selected for collection.
 */
data class UserInfo(
    val name: String,
    val gender: Gender,
    val age: Int,
) {
    val fingerprint: String
        get() = fingerprintOf(name, gender, age)
}

enum class Gender {
    MALE,
    FEMALE,
    UNSPECIFIED,
}

/** Stable identity derived from the normalized profile fields selected by the product. */
fun fingerprintOf(name: String, gender: Gender, age: Int): String {
    val normalized = "${name.trim()}|${gender.name}|$age"
    return MessageDigest.getInstance("SHA-256")
        .digest(normalized.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
