package com.moa.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class RefreshToken(
    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    val tokenHash: String,

    @Column(nullable = false)
    val familyId: String,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Column
    var revokedAt: LocalDateTime? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    fun isActive(now: LocalDateTime): Boolean =
        revokedAt == null && now.isBefore(expiresAt)

    fun revoke(now: LocalDateTime) {
        if (revokedAt == null) revokedAt = now
    }
}
