package com.moa.service

import com.moa.common.auth.JwtTokenProvider
import com.moa.common.auth.RefreshTokenHasher
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import com.moa.entity.RefreshToken
import com.moa.repository.RefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val hasher: RefreshTokenHasher,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun issue(memberId: Long, now: LocalDateTime = LocalDateTime.now()): String =
        persist(memberId, UUID.randomUUID().toString(), now)

    data class RotationResult(val memberId: Long, val plainRefreshToken: String)

    // noRollbackFor: 재사용 감지 시 revoke 가 예외와 함께 롤백되면 안 된다.
    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun rotate(plainRefreshToken: String, now: LocalDateTime = LocalDateTime.now()): RotationResult {
        val current = refreshTokenRepository.findByTokenHash(hasher.hash(plainRefreshToken))
            ?: throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)

        if (current.revokedAt != null) {
            log.warn(
                "Refresh token reuse detected, revoking family. memberId={}, familyId={}",
                current.memberId, current.familyId,
            )
            refreshTokenRepository.revokeAllByFamilyId(current.familyId, now)
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        }
        if (!current.isActive(now)) {
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        }

        // 동시 요청 중 하나만 성공(1), 나머지는 0 → 1토큰 1회전 보장.
        if (refreshTokenRepository.revokeIfActive(current.id, now) == 0) {
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        }
        val newPlain = persist(current.memberId, current.familyId, now)
        return RotationResult(memberId = current.memberId, plainRefreshToken = newPlain)
    }

    @Transactional
    fun revokeByPlainToken(plainRefreshToken: String, now: LocalDateTime = LocalDateTime.now()) {
        val current = refreshTokenRepository.findByTokenHash(hasher.hash(plainRefreshToken)) ?: return
        refreshTokenRepository.revokeAllByFamilyId(current.familyId, now)
    }

    private fun persist(memberId: Long, familyId: String, now: LocalDateTime): String {
        val plain = hasher.generate()
        refreshTokenRepository.save(
            RefreshToken(
                memberId = memberId,
                tokenHash = hasher.hash(plain),
                familyId = familyId,
                expiresAt = jwtTokenProvider.refreshExpiresAt(now),
            )
        )
        return plain
    }
}
