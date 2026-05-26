package com.moa.service.notification

import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Component
class NotificationScheduleClock {
    fun now(): LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Seoul"))

    fun isFutureSchedule(date: LocalDate, time: LocalTime, now: LocalDateTime = now()): Boolean =
        date.atTime(time).isAfter(now)
}
