package com.moa.common.oidc

import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.*

@Component
class AppleClientSecretGenerator(config: OidcProviderConfig) {
    private val apple = config.apple

    private val privateKey: PrivateKey = parse(apple.privateKey)

    private fun parse(raw: String): PrivateKey {
        val body = raw
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(body)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
    }

    fun generate(now: Instant = Instant.now()): String {
        return Jwts.builder()
            .header().keyId(apple.keyId).and()
            .issuer(apple.teamId)
            .subject(apple.clientId)
            .audience().add("https://appleid.apple.com").and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }
}
