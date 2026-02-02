package com.moa.common.oidc

import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*

@Component
class OidcClient(
    private val restClient: RestClient = RestClient.create(),
) {
    fun fetchPublicKeys(jwksUri: String): Map<String, RSAPublicKey> {
        val response = try {
            restClient.get()
                .uri(jwksUri)
                .retrieve()
                .body(JwksResponse::class.java)
        } catch (ex: Exception) {
            throw UnauthorizedException(ErrorCode.OIDC_PROVIDER_ERROR)
        }

        return response?.keys
            ?.filter { it.kty == "RSA" && it.use == "sig" }
            ?.associate { key ->
                key.kid to createRsaPublicKey(key.n, key.e)
            } ?: emptyMap()
    }

    private fun createRsaPublicKey(n: String, e: String): RSAPublicKey {
        val decoder = Base64.getUrlDecoder()
        val modulus = BigInteger(1, decoder.decode(n))
        val exponent = BigInteger(1, decoder.decode(e))
        val spec = RSAPublicKeySpec(modulus, exponent)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec) as RSAPublicKey
    }

    private data class JwksResponse(
        val keys: List<JwkKey>,
    )

    private data class JwkKey(
        val kid: String,
        val kty: String,
        val use: String?,
        val n: String,
        val e: String,
    )
}
