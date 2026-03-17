package com.moa.entity

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 일자별 근무 진행 상태를 정의하는 열거형입니다.
 */
enum class DailyWorkStatusType {
    /** 근무 일정이 없거나 상태를 판정할 수 없는 경우 */
    NONE,

    /** 아직 근무 종료 시각이 지나지 않은 경우 */
    SCHEDULED,

    /** 근무 종료 시각이 지나 완료된 경우 */
    COMPLETED;

    companion object {
        /**
         * 특정 일자의 근무 스케줄 정보를 바탕으로 근무 상태를 판정합니다.
         *
         * 근무 유형이 [DailyWorkScheduleType.NONE]이거나 출근/퇴근 시간 중 하나라도 없으면
         * 상태를 판정할 수 없으므로 [NONE]을 반환합니다.
         * 퇴근 시간이 출근 시간보다 이른 경우에는 자정을 넘기는 근무로 간주하여 익일 종료 시각 기준으로 계산합니다.
         *
         * @param date 근무 상태를 판정할 기준 일자
         * @param scheduleType 해당 일자의 근무 일정 유형
         * @param clockIn 출근 시간
         * @param clockOut 퇴근 시간
         * @param now 상태 판정에 사용할 현재 시각. 기본값은 [LocalDateTime.now] 입니다.
         * @return 판정된 [DailyWorkStatusType]. 종료 시각 이전이면 [SCHEDULED], 이후면 [COMPLETED]를 반환합니다.
         */
        fun resolve(
            date: LocalDate,
            scheduleType: DailyWorkScheduleType,
            clockIn: LocalTime?,
            clockOut: LocalTime?,
            now: LocalDateTime = LocalDateTime.now(),
        ): DailyWorkStatusType {
            if (scheduleType == DailyWorkScheduleType.NONE) return NONE

            val resolvedClockIn = clockIn ?: return NONE
            val resolvedClockOut = clockOut ?: return NONE
            val endAt = if (!resolvedClockOut.isBefore(resolvedClockIn)) {
                date.atTime(resolvedClockOut)
            } else {
                date.plusDays(1).atTime(resolvedClockOut)
            }

            return if (now.isBefore(endAt)) SCHEDULED else COMPLETED
        }
    }
}
