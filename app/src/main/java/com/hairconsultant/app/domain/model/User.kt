package com.hairconsultant.app.domain.model

data class User(
    val id: String,
    val email: String,
    val username: String,
    val birthdayEpochDay: Long,
    val gender: Gender,
    val photoUrl: String? = null,
    val createdAtEpochMillis: Long = 0L,
    /** Last confirmed length/texture/treatment from a consultation, used to personalize future suggestions. */
    val preferredHairLength: HairLength? = null,
    val preferredHairTexture: HairTexture? = null,
    val preferredTreatment: TreatmentPreference? = null
)
