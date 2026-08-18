package com.smarthealth.vitalhub.provider.user

/**
 * The basic information for the participant currently selected for collection.
 */
data class UserInfo(
    val name: String,
    val gender: Gender,
    val age: Int,
)

enum class Gender {
    MALE,
    FEMALE,
    UNSPECIFIED,
}
