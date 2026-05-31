package com.moa.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * FCM 토큰 마스킹 단위 테스트.
 */
class FcmServiceTokenMaskTest {

    @Test
    fun `긴 토큰은 앞 8자 prefix 만 남기고 나머지는 가린다`() {
        val token = "abcdefgh_super_secret_remainder_1234567890"

        val masked = FcmService.maskToken(token)

        assertThat(masked).startsWith("abcdefgh")
        assertThat(masked).doesNotContain("super_secret_remainder")
        assertThat(masked).doesNotContain(token)
    }

    @Test
    fun `8자 이하의 짧은 토큰은 전체를 가린다`() {
        val token = "short"

        val masked = FcmService.maskToken(token)

        assertThat(masked).doesNotContain("short")
    }

    @Test
    fun `빈 토큰도 예외 없이 가려진 표현을 반환한다`() {
        val masked = FcmService.maskToken("")

        assertThat(masked).isNotEmpty()
    }
}
