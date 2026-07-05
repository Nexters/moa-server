package com.moa.common.oidc

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

@Component
class AppleClientSecretGenerator(
    @Value("\${oidc.apple.team-id:}") private val teamId: String,
    @Value("\${oidc.apple.client-id:}") private val clientId: String,
    @Value("\${oidc.apple.key-id:}") private val keyId: String,
    @Value("\${oidc.apple.private-key:}") privateKeyBase64: String,
) {
    private val privateKey: PrivateKey? =
        privateKeyBase64.takeIf { it.isNotBlank() }?.let { parse(it) }

    private fun parse(raw: String): PrivateKey {
        val body = raw
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(body)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    fun generate(now: Instant = Instant.now()): String {
        val key = requireNotNull(privateKey) { "Apple private key(.p8)가 설정되지 않았습니다" }
        return Jwts.builder()
            .header().keyId(keyId).and()
            .issuer(teamId)
            .subject(clientId)
            .audience().add("https://appleid.apple.com").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
            .signWith(key, Jwts.SIG.ES256)
            .compact()
    }
}
