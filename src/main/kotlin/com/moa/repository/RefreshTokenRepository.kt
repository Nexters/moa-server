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
}
