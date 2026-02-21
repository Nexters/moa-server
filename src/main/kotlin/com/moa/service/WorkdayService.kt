package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkSchedule
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.MonthlyWorkdayResponse
import com.moa.service.dto.WorkdayEditRequest
import com.moa.service.dto.WorkdayResponse
import com.moa.service.dto.WorkdayUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class WorkdayService(
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val notificationSyncService: NotificationSyncService,
) {

    @Transactional(readOnly = true)
    fun getSchedule(
        memberId: Long,
        date: LocalDate,
    ): WorkdayResponse {
        // 1. 저장된 스케줄이 있으면 최우선
        val savedSchedule =
            dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)

        if (savedSchedule != null) {
            return WorkdayResponse(
                date = savedSchedule.date,
                type = savedSchedule.type,
                clockInTime = savedSchedule.clockInTime,
                clockOutTime = savedSchedule.clockOutTime,
            )
        }

        // 2. 월 대표 정책 기준으로 판단
        val monthlyPolicy =
            resolveMonthlyRepresentativePolicy(memberId, date.year, date.monthValue)

        // 4. 요일 기준 WORK / NONE
        val isWorkday =
            monthlyPolicy.workdays.any {
                it.dayOfWeek == date.dayOfWeek
            }

        return if (isWorkday) {
            WorkdayResponse(
                date = date,
                type = DailyWorkScheduleType.WORK,
                clockInTime = monthlyPolicy.clockInTime,
                clockOutTime = monthlyPolicy.clockOutTime,
            )
        } else {
            WorkdayResponse(
                date = date,
                type = DailyWorkScheduleType.NONE,
                clockInTime = null,
                clockOutTime = null,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getMonthlySchedules(
        memberId: Long,
        year: Int,
        month: Int,
    ): List<MonthlyWorkdayResponse> {

        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())

        val savedSchedulesByDate =
            dailyWorkScheduleRepository
                .findAllByMemberIdAndDateBetween(memberId, start, end)
                .associateBy { it.date }

        val monthlyPolicy =
            resolveMonthlyRepresentativePolicy(memberId, year, month)

        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .map { date ->
                savedSchedulesByDate[date]?.let {
                    MonthlyWorkdayResponse(
                        date = date,
                        type = it.type,
                    )
                } ?: run {
                    val type =
                        if (monthlyPolicy.workdays.any { it.dayOfWeek == date.dayOfWeek }) {
                            DailyWorkScheduleType.WORK
                        } else {
                            DailyWorkScheduleType.NONE
                        }

                    MonthlyWorkdayResponse(
                        date = date,
                        type = type,
                    )
                }
            }
            .toList()
    }

    @Transactional
    fun upsertSchedule(memberId: Long, date: LocalDate, req: WorkdayUpsertRequest): WorkdayResponse {
        val (clockIn, clockOut) = when (req.type) {
            DailyWorkScheduleType.WORK -> {
                val clockIn = req.clockInTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                val clockOut = req.clockOutTime ?: throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                clockIn to clockOut
            }

            DailyWorkScheduleType.VACATION -> null to null

            DailyWorkScheduleType.NONE -> throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
        }

        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.apply {
                this.type = req.type
                this.clockInTime = clockIn
                this.clockOutTime = clockOut
            }
            ?: DailyWorkSchedule(
                memberId = memberId,
                date = date,
                type = req.type,
                clockInTime = clockIn,
                clockOutTime = clockOut,
            )

        val savedSchedule = dailyWorkScheduleRepository.save(workSchedule)

        notificationSyncService.syncNotifications(
            memberId, date, savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime,
        )

        return WorkdayResponse(
            date = savedSchedule.date,
            type = savedSchedule.type,
            clockInTime = savedSchedule.clockInTime,
            clockOutTime = savedSchedule.clockOutTime,
        )
    }

    @Transactional
    fun patchClockOut(memberId: Long, date: LocalDate, req: WorkdayEditRequest): WorkdayResponse {
        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.also {
                if (it.type == DailyWorkScheduleType.VACATION) {
                    throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
                }
            }
            ?: run {
                val policy = workPolicyVersionRepository
                    .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
                    ?: throw NotFoundException()
                DailyWorkSchedule(
                    memberId = memberId,
                    date = date,
                    type = DailyWorkScheduleType.WORK,
                    clockInTime = policy.clockInTime,
                    clockOutTime = policy.clockOutTime,
                )
            }

        workSchedule.clockOutTime?.let { clockOut ->
            if (clockOut.isAfter(req.clockOutTime)) {
                throw BadRequestException(ErrorCode.INVALID_WORKDAY_INPUT)
            }
        }

        workSchedule.clockOutTime = req.clockOutTime

        val savedSchedule = dailyWorkScheduleRepository.save(workSchedule)

        notificationSyncService.syncNotifications(
            memberId, date, DailyWorkScheduleType.WORK, savedSchedule.clockInTime, savedSchedule.clockOutTime,
        )

        return WorkdayResponse(
            date = savedSchedule.date,
            type = savedSchedule.type,
            clockInTime = savedSchedule.clockInTime,
            clockOutTime = savedSchedule.clockOutTime,
        )
    }

    private fun resolveMonthlyRepresentativePolicy(
        memberId: Long,
        year: Int,
        month: Int,
    ): WorkPolicyVersion {

        val lastDayOfMonth =
            LocalDate.of(year, month, 1)
                .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())

        return workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                memberId,
                lastDayOfMonth,
            )
            ?: throw IllegalStateException("해당 월의 마지막 날에 적용 가능한 근무 정책이 존재하지 않습니다. memberId=$memberId, year=$year, month=$month")
    }
}
