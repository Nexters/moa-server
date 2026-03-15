package com.moa.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

// 월급일이 해당 월에 없으면 말일로 보정하고, 그 날짜가 주말이면 직전 금요일로 당긴다.
fun resolveEffectivePayday(year: Int, month: Int, paydayDay: Int): LocalDate {
    val yearMonth = YearMonth.of(year, month)
    val baseDate = yearMonth.atDay(minOf(paydayDay, yearMonth.lengthOfMonth()))

    return when (baseDate.dayOfWeek) {
        DayOfWeek.SATURDAY -> baseDate.minusDays(1)
        DayOfWeek.SUNDAY -> baseDate.minusDays(2)
        else -> baseDate
    }
}
