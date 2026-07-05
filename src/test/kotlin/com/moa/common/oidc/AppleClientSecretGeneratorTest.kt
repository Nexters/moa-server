package com.moa.common.oidc

import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AppleClientSecretGeneratorTest {
    @Test
    fun `생성한 client_secret 은 공개키로 검증되고 클레임이 맞다`() {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val kp = gen.generateKeyPair()
        val privB64 = Base64.getEncoder().encodeToString(kp.private.encoded)

        val sut = AppleClientSecretGenerator(
            OidcProviderConfig(
                kakao = OidcProviderConfig.KakaoProviderProperties(jwksUri = "unused"),
                apple = OidcProviderConfig.AppleProviderProperties(
                    jwksUri = "unused",
                    clientId = "kr.moa-official.web",
                    teamId = "TEAM123456",
                    keyId = "KEY1234567",
                    privateKey = privB64,
                    redirectUri = "https://api.moa.example/apple/desktop/callback",
                ),
            ),
        )

        val jwt = sut.generate()

        val parsed = Jwts.parser().verifyWith(kp.public).build().parseSignedClaims(jwt)
        assertThat(parsed.header["kid"]).isEqualTo("KEY1234567")
        assertThat(parsed.header.algorithm).isEqualTo("ES256")
        assertThat(parsed.payload.issuer).isEqualTo("TEAM123456")
        assertThat(parsed.payload.subject).isEqualTo("kr.moa-official.web")
        assertThat(parsed.payload.audience).contains("https://appleid.apple.com")
    }
}
