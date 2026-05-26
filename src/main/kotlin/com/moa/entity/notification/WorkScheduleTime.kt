package com.moa.entity.notification

import java.time.LocalDate
import java.time.LocalTime

class WorkScheduleTime private constructor(
    val clockInTime: LocalTime,
    val clockOutTime: LocalTime,
) {
    val isMidnightCrossing: Boolean = clockOutTime < clockInTime

    fun clockOutDate(baseDate: LocalDate): LocalDate =
        if (isMidnightCrossing) baseDate.plusDays(1) else baseDate

    companion object {
        fun of(clockIn: LocalTime, clockOut: LocalTime) = WorkScheduleTime(
            clockInTime = normalize(clockIn),
            clockOutTime = normalize(clockOut),
        )

        fun normalize(time: LocalTime): LocalTime = LocalTime.of(time.hour, time.minute)
    }
}
