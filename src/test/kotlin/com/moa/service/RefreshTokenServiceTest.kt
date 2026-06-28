package com.moa.service

import com.moa.common.auth.JwtTokenProvider
import com.moa.common.auth.RefreshTokenHasher
import com.moa.entity.RefreshToken
import com.moa.repository.RefreshTokenRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class RefreshTokenServiceTest {

    private val repository = mockk<RefreshTokenRepository>(relaxed = true)
    private val hasher = mockk<RefreshTokenHasher>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>()
    private val sut = RefreshTokenService(repository, hasher, jwtTokenProvider)

    @Test
    fun `issue 는 새 familyId 행을 저장하고 평문 토큰을 반환한다`() {
        every { hasher.generate() } returns "plain-1"
        every { hasher.hash("plain-1") } returns "hash-1"
        every { jwtTokenProvider.refreshExpiresAt(any()) } returns LocalDateTime.now().plusDays(30)
        val saved = slot<RefreshToken>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val plain = sut.issue(memberId = 1L)

        assertThat(plain).isEqualTo("plain-1")
        assertThat(saved.captured.memberId).isEqualTo(1L)
        assertThat(saved.captured.tokenHash).isEqualTo("hash-1")
        assertThat(saved.captured.familyId).isNotBlank()
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `rotate 는 옛 행을 revoke 하고 같은 familyId 새 행을 만들어 새 평문을 반환한다`() {
        val now = LocalDateTime.now()
        val old = RefreshToken(memberId = 1L, tokenHash = "old-hash", familyId = "fam-1", expiresAt = now.plusDays(10))
        every { hasher.hash("old-plain") } returns "old-hash"
        every { repository.findByTokenHash("old-hash") } returns old
        every { repository.revokeIfActive(old.id, now) } returns 1
        every { hasher.generate() } returns "new-plain"
        every { hasher.hash("new-plain") } returns "new-hash"
        every { jwtTokenProvider.refreshExpiresAt(any()) } returns now.plusDays(30)
        val saved = slot<RefreshToken>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        val result = sut.rotate("old-plain", now)

        assertThat(result.plainRefreshToken).isEqualTo("new-plain")
        assertThat(result.memberId).isEqualTo(1L)
        verify { repository.revokeIfActive(old.id, now) }
        assertThat(saved.captured.familyId).isEqualTo("fam-1")
        assertThat(saved.captured.tokenHash).isEqualTo("new-hash")
    }

    @Test
    fun `rotate 는 동시 요청으로 이미 회전된 토큰이면 새 토큰을 발급하지 않고 예외를 던진다`() {
        // revokeIfActive 가 0 을 반환 = 동시 요청이 먼저 회전함 → 1토큰 1회전 보장(double-spend 차단).
        val now = LocalDateTime.now()
        val active = RefreshToken(memberId = 1L, tokenHash = "h", familyId = "fam-1", expiresAt = now.plusDays(10))
        every { hasher.hash("plain") } returns "h"
        every { repository.findByTokenHash("h") } returns active
        every { repository.revokeIfActive(active.id, now) } returns 0

        org.junit.jupiter.api.assertThrows<com.moa.common.exception.UnauthorizedException> {
            sut.rotate("plain", now)
        }

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `rotate 는 이미 revoked 된 토큰 재사용 시 familyId 전체를 revoke 하고 예외를 던진다`() {
        val now = LocalDateTime.now()
        val revoked = RefreshToken(memberId = 1L, tokenHash = "h", familyId = "fam-1", expiresAt = now.plusDays(10))
            .apply { revoke(now.minusMinutes(1)) }
        every { hasher.hash("reused") } returns "h"
        every { repository.findByTokenHash("h") } returns revoked

        org.junit.jupiter.api.assertThrows<com.moa.common.exception.UnauthorizedException> {
            sut.rotate("reused", now)
        }

        verify { repository.revokeAllByFamilyId("fam-1", now) }
    }

    @Test
    fun `rotate 는 존재하지 않는 토큰이면 EXPIRED_TOKEN 으로 예외를 던진다`() {
        every { hasher.hash("ghost") } returns "ghost-hash"
        every { repository.findByTokenHash("ghost-hash") } returns null

        val ex = org.junit.jupiter.api.assertThrows<com.moa.common.exception.UnauthorizedException> {
            sut.rotate("ghost", LocalDateTime.now())
        }
        // 무효한 refresh 의 모든 케이스(없음/폐기/만료)를 동일 코드로 통일 — 클라이언트 일관 + 존재 비노출.
        assertThat(ex.errorCode).isEqualTo(com.moa.common.exception.ErrorCode.EXPIRED_TOKEN)
    }

    @Test
    fun `rotate 는 만료된 토큰이면 예외를 던진다`() {
        val now = LocalDateTime.now()
        val expired = RefreshToken(memberId = 1L, tokenHash = "h", familyId = "fam-1", expiresAt = now.minusDays(1))
        every { hasher.hash("old") } returns "h"
        every { repository.findByTokenHash("h") } returns expired

        org.junit.jupiter.api.assertThrows<com.moa.common.exception.UnauthorizedException> {
            sut.rotate("old", now)
        }
    }

    @Test
    fun `rotate 는 revoked 이면서 만료된 토큰도 재사용으로 보고 familyId 전체를 revoke 한다`() {
        // revoke 검사가 만료 검사보다 먼저여야 한다 — 둘 다인 토큰도 탈취 신호로 처리.
        val now = LocalDateTime.now()
        val revokedAndExpired = RefreshToken(memberId = 1L, tokenHash = "h", familyId = "fam-1", expiresAt = now.minusDays(2))
            .apply { revoke(now.minusDays(1)) }
        every { hasher.hash("reused-expired") } returns "h"
        every { repository.findByTokenHash("h") } returns revokedAndExpired

        org.junit.jupiter.api.assertThrows<com.moa.common.exception.UnauthorizedException> {
            sut.rotate("reused-expired", now)
        }

        verify { repository.revokeAllByFamilyId("fam-1", now) }
    }

    @Test
    fun `revokeByPlainToken 은 해당 familyId 체인을 무효화한다`() {
        val now = LocalDateTime.now()
        val token = RefreshToken(memberId = 1L, tokenHash = "h", familyId = "fam-1", expiresAt = now.plusDays(10))
        every { hasher.hash("plain") } returns "h"
        every { repository.findByTokenHash("h") } returns token

        sut.revokeByPlainToken("plain", now)

        verify { repository.revokeAllByFamilyId("fam-1", now) }
    }

    @Test
    fun `revokeByPlainToken 은 없는 토큰이면 조용히 무시한다`() {
        every { hasher.hash("ghost") } returns "gh"
        every { repository.findByTokenHash("gh") } returns null

        sut.revokeByPlainToken("ghost", LocalDateTime.now())

        verify(exactly = 0) { repository.revokeAllByFamilyId(any(), any()) }
    }
}
