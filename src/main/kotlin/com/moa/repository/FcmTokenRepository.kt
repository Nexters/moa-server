package com.moa.repository

import com.moa.entity.FcmToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    fun findAllByMemberId(memberId: Long): List<FcmToken>
    fun findByToken(token: String): FcmToken?

    @Transactional
    fun deleteByToken(token: String)
    fun findAllByMemberIdIn(memberIds: Collection<Long>): List<FcmToken>
    fun deleteAllByMemberId(memberId: Long)
}
