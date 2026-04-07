package com.moa.repository

import com.moa.entity.PublicHoliday
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PublicHolidayRepository : JpaRepository<PublicHoliday, Long> {

    fun findAllByDateBetween(start: LocalDate, end: LocalDate): List<PublicHoliday>

    fun existsByDate(date: LocalDate): Boolean
}
