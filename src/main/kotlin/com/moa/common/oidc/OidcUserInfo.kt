package com.moa.common.oidc

import com.moa.entity.ProviderType

data class OidcUserInfo(
    val subject: String,
    val provider: ProviderType,
)
