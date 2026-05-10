package com.moa.service

import com.moa.common.exception.NotFoundException
import com.moa.entity.PublicHoliday
import com.moa.repository.PublicHolidayRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PublicHolidayService(
    private val publicHolidayRepository: PublicHolidayRepository,
) {

    @Transactional(readOnly = true)
    fun getHolidayDatesForMonth(year: Int, month: Int): Set<LocalDate> {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        return publicHolidayRepository.findAllByDateBetween(start, end)
            .map { it.date }
            .toSet()
    }

    @Transactional(readOnly = true)
    fun getHolidayDatesForPaydayResolution(date: LocalDate): Set<LocalDate> {
        val start = date.withDayOfMonth(1)
        val nextMonth = start.plusMonths(1)
        val end = nextMonth.withDayOfMonth(nextMonth.lengthOfMonth())
        return publicHolidayRepository.findAllByDateBetween(start, end)
            .map { it.date }
            .toSet()
    }

    @Transactional(readOnly = true)
    fun isHoliday(date: LocalDate): Boolean =
        publicHolidayRepository.existsByDate(date)

    @Transactional(readOnly = true)
    fun getByYear(year: Int): List<PublicHoliday> {
        val start = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        return publicHolidayRepository.findAllByDateBetween(start, end)
    }

    @Transactional
    fun create(date: LocalDate, name: String): PublicHoliday =
        publicHolidayRepository.save(PublicHoliday(date = date, name = name))

    @Transactional
    fun delete(id: Long) {
        if (!publicHolidayRepository.existsById(id)) {
            throw NotFoundException()
        }
        publicHolidayRepository.deleteById(id)
    }
}
