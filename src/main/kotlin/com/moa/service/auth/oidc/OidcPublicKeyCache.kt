package com.moa.service.auth.oidc

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import org.springframework.stereotype.Component
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class OidcPublicKeyCache(
    private val oidcClient: OidcClient,
) {
    private data class CacheEntry(
        val keys: Map<String, RSAPublicKey>,
        val expiresAt: Instant,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun getPublicKey(jwksUri: String, kid: String, ttlSeconds: Long): RSAPublicKey {
        val entry = cache[jwksUri]
        val now = Instant.now()

        if (entry != null && entry.expiresAt.isAfter(now)) {
            entry.keys[kid]?.let { return it }
        }

        return refreshAndGetKey(jwksUri, kid, ttlSeconds)
    }

    private fun refreshAndGetKey(jwksUri: String, kid: String, ttlSeconds: Long): RSAPublicKey {
        val keys = oidcClient.fetchPublicKeys(jwksUri)
        val expiresAt = Instant.now().plusSeconds(ttlSeconds)
        cache[jwksUri] = CacheEntry(keys, expiresAt)

        return keys[kid]
            ?: throw BadRequestException(ErrorCode.INVALID_ID_TOKEN);
    }

    //TODO : 혹시 강제 초기화 필요할 때를 대비해 남겨둠
    fun clear() {
        cache.clear()
    }
}
