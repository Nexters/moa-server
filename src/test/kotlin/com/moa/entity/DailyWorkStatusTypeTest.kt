package com.moa.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DailyWorkStatusTypeTest {

    private val date = LocalDate.of(2025, 6, 9)

    @Test
    fun `근무 유형이 NONE이면 NONE을 반환한다`() {
        val result = DailyWorkStatusType.resolve(
            date = date,
            scheduleType = DailyWorkScheduleType.NONE,
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(18, 0),
            now = date.atTime(12, 0),
        )

        assertThat(result).isEqualTo(DailyWorkStatusType.NONE)
    }

    @Test
    fun `종료 시각 전이면 SCHEDULED를 반환한다`() {
        val result = DailyWorkStatusType.resolve(
            date = date,
            scheduleType = DailyWorkScheduleType.WORK,
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(18, 0),
            now = date.atTime(17, 59),
        )

        assertThat(result).isEqualTo(DailyWorkStatusType.SCHEDULED)
    }

    @Test
    fun `종료 시각 이후면 COMPLETED를 반환한다`() {
        val result = DailyWorkStatusType.resolve(
            date = date,
            scheduleType = DailyWorkScheduleType.WORK,
            clockIn = LocalTime.of(9, 0),
            clockOut = LocalTime.of(18, 0),
            now = date.atTime(18, 0),
        )

        assertThat(result).isEqualTo(DailyWorkStatusType.COMPLETED)
    }

    @Test
    fun `자정 넘김 근무는 익일 종료 시각 기준으로 판정한다`() {
        val result = DailyWorkStatusType.resolve(
            date = date,
            scheduleType = DailyWorkScheduleType.WORK,
            clockIn = LocalTime.of(22, 0),
            clockOut = LocalTime.of(2, 0),
            now = LocalDateTime.of(2025, 6, 10, 1, 0),
        )

        assertThat(result).isEqualTo(DailyWorkStatusType.SCHEDULED)
    }
}
