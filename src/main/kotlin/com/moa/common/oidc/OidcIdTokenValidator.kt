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
        val providerProperties = getProviderProperties(provider)

        return validateToken(idToken, providerProperties, provider)
    }

    private fun getProviderProperties(provider: ProviderType): OidcProviderConfig.ProviderProperties {
        return when (provider) {
            ProviderType.KAKAO -> config.kakao
            else -> throw UnauthorizedException(ErrorCode.INVALID_PROVIDER)
        }
    }

    private fun validateToken(
        idToken: String,
        providerConfig: OidcProviderConfig.ProviderProperties,
        provider: ProviderType
    ): OidcUserInfo {
        val kid = extractKid(idToken)

        val publicKey = publicKeyCache.getPublicKey(
            jwksUri = providerConfig.jwksUri,
            kid = kid,
            ttlSeconds = providerConfig.cacheTtlSeconds,
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
