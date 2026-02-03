package com.moa.common.oidc

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oidc")
data class OidcProviderConfig(
    val kakao: ProviderProperties,
) {
    data class ProviderProperties(
        val jwksUri: String,
        val cacheTtlSeconds: Long = 3600,
    )
}
