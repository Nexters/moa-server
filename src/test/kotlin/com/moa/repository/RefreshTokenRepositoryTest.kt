package com.moa.repository

import com.moa.entity.RefreshToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@DataJpaTest
class RefreshTokenRepositoryTest @Autowired constructor(
    private val refreshTokenRepository: RefreshTokenRepository,
) {

    @Test
    fun `tokenHash 로 조회한다`() {
        val saved = refreshTokenRepository.save(
            RefreshToken(memberId = 1L, tokenHash = "hash-1", familyId = "fam-1", expiresAt = future),
        )

        val found = refreshTokenRepository.findByTokenHash("hash-1")

        assertThat(found?.id).isEqualTo(saved.id)
    }

    @Test
    fun `없는 해시는 null 을 반환한다`() {
        assertThat(refreshTokenRepository.findByTokenHash("nope")).isNull()
    }

    @Test
    fun `familyId 의 활성 행을 모두 revoke 한다`() {
        refreshTokenRepository.save(RefreshToken(1L, "h1", "fam-1", future))
        refreshTokenRepository.save(RefreshToken(1L, "h2", "fam-1", future))
        refreshTokenRepository.save(RefreshToken(1L, "h3", "fam-2", future))

        val now = LocalDateTime.now()
        val updated = refreshTokenRepository.revokeAllByFamilyId("fam-1", now)

        assertThat(updated).isEqualTo(2)
        assertThat(refreshTokenRepository.findByTokenHash("h3")!!.revokedAt).isNull()
    }

    @Test
    fun `이미 revoke 된 행은 다시 덮어쓰지 않는다`() {
        refreshTokenRepository.save(RefreshToken(memberId = 1L, tokenHash = "active-1", familyId = "fam-1", expiresAt = future))
        refreshTokenRepository.save(RefreshToken(memberId = 1L, tokenHash = "active-2", familyId = "fam-1", expiresAt = future))
        // DB datetime(6) 은 마이크로초까지만 저장하므로, 나노초를 잘라 왕복 후에도 동일하게 비교한다.
        val alreadyRevokedAt = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)
        val preRevoked = refreshTokenRepository.save(
            RefreshToken(memberId = 1L, tokenHash = "revoked-1", familyId = "fam-1", expiresAt = future)
                .apply { revoke(alreadyRevokedAt) }
        )

        val now = LocalDateTime.now()
        val updated = refreshTokenRepository.revokeAllByFamilyId("fam-1", now)

        assertThat(updated).isEqualTo(2)
        assertThat(refreshTokenRepository.findByTokenHash("revoked-1")!!.revokedAt).isEqualTo(alreadyRevokedAt)
    }

    companion object {
        private val future: LocalDateTime = LocalDateTime.now().plusDays(30)
    }
}
