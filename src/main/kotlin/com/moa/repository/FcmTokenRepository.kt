package com.moa.repository

import com.moa.entity.FcmToken
import org.springframework.data.jpa.repository.JpaRepository

interface FcmTokenRepository : JpaRepository<FcmToken, Long> {
    fun findByToken(token: String): FcmToken?
    fun deleteByToken(token: String)
}
