package com.moa.service.auth.oidc

data class OidcUserInfo(
    val subject: String,
    val email: String?,
    val nickname: String?,
    val provider: ProviderType,
)
