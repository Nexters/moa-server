package com.moa.repository

import com.moa.entity.Profile
import com.moa.entity.PaydayDay
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileRepository : JpaRepository<Profile, Long> {
    fun findByMemberId(memberId: Long): Profile?
    fun findAllByPaydayDayIn(paydayDays: Collection<PaydayDay>): List<Profile>
}
