package com.moa.common.oidc.apple

import com.fasterxml.jackson.annotation.JsonProperty
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import com.moa.common.oidc.OidcProviderConfig
import io.jsonwebtoken.Jwts
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Component
class AppleOAuthClient(
    private val config: OidcProviderConfig,
    private val restClient: RestClient = RestClient.create(),
) {
    private val appleConfig get() = config.apple

    fun exchangeCodeForIdToken(authorizationCode: String): String {
        val clientSecret = generateClientSecret()

        val response = restClient.post()
            .uri("https://appleid.apple.com/auth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(LinkedMultiValueMap<String, String>().apply {
                add("client_id", appleConfig.clientId)
                add("client_secret", clientSecret)
                add("code", authorizationCode)
                add("grant_type", "authorization_code")
                add("redirect_uri", appleConfig.redirectUri)
            })
            .retrieve()
            .body(AppleTokenResponse::class.java)

        return response?.idToken
            ?: throw UnauthorizedException(ErrorCode.APPLE_TOKEN_EXCHANGE_FAILED)
    }

    private fun generateClientSecret(): String {
        val now = LocalDateTime.now().atZone(ZoneId.of("Asia/Seoul")).toInstant()
        return Jwts.builder()
            .header().keyId(appleConfig.keyId).and()
            .issuer(appleConfig.teamId)
            .subject(appleConfig.clientId)
            .audience().add("https://appleid.apple.com").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(300)))
            .signWith(loadPrivateKey(), Jwts.SIG.ES256)
            .compact()
    }

    private fun loadPrivateKey(): ECPrivateKey {
        val keyContent = appleConfig.privateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(keyContent)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(keySpec) as ECPrivateKey
    }

    private data class AppleTokenResponse(
        @JsonProperty("id_token") val idToken: String?,
        @JsonProperty("access_token") val accessToken: String?,
        @JsonProperty("refresh_token") val refreshToken: String?,
    )
}
