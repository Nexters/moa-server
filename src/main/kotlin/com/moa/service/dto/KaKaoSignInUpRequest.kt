package com.moa.service.dto

data class KaKaoSignInUpRequest(
    val idToken: String,
    val fcmDeviceToken: String? = null,
) {
}
