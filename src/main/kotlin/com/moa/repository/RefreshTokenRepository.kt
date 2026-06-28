package com.moa.repository

import com.moa.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update RefreshToken r set r.revokedAt = :now " +
                "where r.familyId = :familyId and r.revokedAt is null"
    )
    fun revokeAllByFamilyId(familyId: String, now: LocalDateTime): Int

    /**
     * 활성(revokedAt is null) 행을 원자적으로 revoke 한다. 영향 행 수가 1이면 이 호출이 회전 권한을 얻은 것,
     * 0이면 동시 요청이 먼저 가져간 것이다. 동일 토큰 동시 회전(double-spend)을 DB 레벨에서 차단한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        "update RefreshToken r set r.revokedAt = :now " +
                "where r.id = :id and r.revokedAt is null"
    )
    fun revokeIfActive(id: Long, now: LocalDateTime): Int
}
