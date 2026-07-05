package com.moa.common.auth

import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class RefreshTokenHasher {

    fun generate(): String = OpaqueTokenGenerator.generate(TOKEN_BYTES)

    fun hash(plain: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plain.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_BYTES = 32
    }
}
