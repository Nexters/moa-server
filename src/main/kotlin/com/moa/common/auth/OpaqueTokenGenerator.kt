package com.moa.common.auth

import java.security.SecureRandom
import java.util.Base64

/** URL-safe 불투명 토큰 생성기. refresh token 과 exchange code 가 같은 엔트로피 정책을 공유한다. */
object OpaqueTokenGenerator {

    private val random = SecureRandom()

    fun generate(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
