package com.moa.common.oidc

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oidc")
data class OidcProviderConfig(
    val kakao: KakaoProviderProperties,
    val apple: AppleProviderProperties,
) {
    data class KakaoProviderProperties(
        val jwksUri: String,
        val cacheTtlSeconds: Long = 3600,
    )

    data class AppleProviderProperties(
        val jwksUri: String,
        val cacheTtlSeconds: Long = 3600,
    )
}
