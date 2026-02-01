package com.moa.entity

import java.time.DayOfWeek
import java.time.LocalDate

enum class Workday(
    val dayOfWeek: DayOfWeek,
) {
    MON(DayOfWeek.MONDAY),
    TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY),
    SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    companion object {

        fun from(date: LocalDate): Workday =
            from(date.dayOfWeek)

        fun from(dayOfWeek: DayOfWeek): Workday =
            entries.first { it.dayOfWeek == dayOfWeek }

        val WEEKDAYS = setOf(MON, TUE, WED, THU, FRI)

        val WEEKENDS = setOf(SAT, SUN)
    }
}
