package com.moa.common.oidc

import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import com.moa.entity.ProviderType
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.*

@Component
class OidcIdTokenValidator(
    private val config: OidcProviderConfig,
    private val publicKeyCache: OidcPublicKeyCache,
    private val objectMapper: ObjectMapper
) {
    fun validate(provider: ProviderType, idToken: String): OidcUserInfo {
        return validateToken(idToken, provider)
    }

    private fun getJwksConfig(provider: ProviderType): Pair<String, Long> {
        return when (provider) {
            ProviderType.KAKAO -> config.kakao.jwksUri to config.kakao.cacheTtlSeconds
            ProviderType.APPLE -> config.apple.jwksUri to config.apple.cacheTtlSeconds
        }
    }

    private fun validateToken(
        idToken: String,
        provider: ProviderType
    ): OidcUserInfo {
        val (jwksUri, cacheTtlSeconds) = getJwksConfig(provider)
        val kid = extractKid(idToken)

        val publicKey = publicKeyCache.getPublicKey(
            jwksUri = jwksUri,
            kid = kid,
            ttlSeconds = cacheTtlSeconds,
        )

        val claims = try {
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(idToken)
                .payload
        } catch (ex: Exception) {
            throw UnauthorizedException(ErrorCode.INVALID_ID_TOKEN)
        }

        return OidcUserInfo(
            subject = claims.subject,
            provider = provider
        )
    }


    private fun extractKid(idToken: String): String {
        try {
            val headerPart = idToken.split(".")[0]
            val decodedHeader = String(Base64.getUrlDecoder().decode(headerPart))

            val headerMap = objectMapper.readValue(decodedHeader, Map::class.java)

            return headerMap["kid"] as? String
                ?: throw UnauthorizedException(ErrorCode.INVALID_ID_TOKEN)

        } catch (ex: Exception) {
            throw UnauthorizedException(ErrorCode.INVALID_ID_TOKEN)
        }
    }
}
