package com.moa.repository

import com.moa.entity.DailyWorkSchedule
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyWorkScheduleRepository : JpaRepository<DailyWorkSchedule, Long> {
    fun findByMemberIdAndDate(memberId: Long, date: LocalDate): DailyWorkSchedule?
    fun findAllByMemberIdInAndDate(memberIds: Collection<Long>, date: LocalDate): List<DailyWorkSchedule>
    fun findAllByMemberIdAndDateBetween(
        memberId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyWorkSchedule>
}
