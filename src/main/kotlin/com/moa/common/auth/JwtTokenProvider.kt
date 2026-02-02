package com.moa.common.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Component
class JwtTokenProvider(
    @Value("\${jwt.secret-key}")
    private val accessTokenSecretKey: String,

    @Value("\${jwt.expiration-milliseconds}")
    private val accessTokenExpirationInMilliseconds: Long,
) {

    private val accessKey = Keys.hmacShaKeyFor(accessTokenSecretKey.toByteArray(StandardCharsets.UTF_8))

    fun createAccessToken(userId: Long): String {
        val now = LocalDateTime.now()
        val expiryDate = now.plus(Duration.ofMillis(accessTokenExpirationInMilliseconds))

        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(toDate(now))
            .expiration(toDate(expiryDate))
            .signWith(accessKey)
            .compact()
    }

    fun extractToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        return if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            bearerToken.substring(7)
        } else null
    }

    fun getUserIdFromToken(token: String): Long? {
        return getClaims(token).subject.toLong()
    }

    fun validateToken(token: String): Boolean {
        return try {
            getClaims(token)
            true
        } catch (ex: Exception) {
            false
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(accessKey)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}

fun toDate(localDateTime: LocalDateTime): Date {
    return Date.from(localDateTime.atZone(ZoneId.of("Asia/Seoul")).toInstant())
}
