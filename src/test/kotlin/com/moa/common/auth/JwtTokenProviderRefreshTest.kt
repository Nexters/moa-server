package com.moa.common.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class JwtTokenProviderRefreshTest {

    private val sut = JwtTokenProvider(
        accessTokenSecretKey = "test-secret-key-test-secret-key-test-secret-key",
        accessTokenExpirationInMilliseconds = 1_800_000,
        refreshTokenExpirationInMilliseconds = 2_592_000_000,
    )

    @Test
    fun `refresh 만료 시각은 기준 시각 + 설정된 TTL 이다`() {
        val from = LocalDateTime.of(2026, 6, 27, 12, 0)

        val expiresAt = sut.refreshExpiresAt(from)

        assertThat(expiresAt).isEqualTo(from.plusDays(30))
    }
}
