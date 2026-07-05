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
        // 데스크톱 로그인 자격증명 — 전부 필수. 누락은 바인딩 실패
        val clientId: String,
        val teamId: String,
        val keyId: String,
        val privateKey: String,
        val redirectUri: String,
        val desktopRedirectUri: String = "http://127.0.0.1:17171/callback",
        val tokenUri: String = "https://appleid.apple.com/auth/token",
        val tokenConnectTimeoutSeconds: Long = 3,
        val tokenReadTimeoutSeconds: Long = 5,
    ) {
        init {
            val required = mapOf(
                "client-id" to clientId,
                "team-id" to teamId,
                "key-id" to keyId,
                "private-key" to privateKey,
                "redirect-uri" to redirectUri,
            )
            require(required.values.none { it.isBlank() }) {
                "oidc.apple 필수 설정이 비어 있습니다: ${required.filterValues { it.isBlank() }.keys}"
            }
        }
    }
}
