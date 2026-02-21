package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkSchedule
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.SalaryCalculator
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.*
import com.moa.service.notification.NotificationSyncService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

@Service
class WorkdayService(
    private val dailyWorkScheduleRepository: DailyWorkScheduleRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
    private val notificationSyncService: NotificationSyncService,
    private val earningsCalculator: EarningsCalculator,
) {

    @Transactional(readOnly = true)
    fun getSchedule(
        memberId: Long,
        date: LocalDate,
    ): WorkdayResponse {
        val saved = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        val policy = resolveMonthlyRepresentativePolicy(memberId, date.year, date.monthValue)
        val schedule = resolveScheduleForDate(saved, policy, date)
        return createWorkdayResponse(memberId, date, schedule)
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
                val schedule = resolveScheduleForDate(savedSchedulesByDate[date], monthlyPolicy, date)
                MonthlyWorkdayResponse(date = date, type = schedule.type)
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

        val schedule = ResolvedSchedule(savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime)
        return createWorkdayResponse(memberId, date, schedule)
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

        val schedule = ResolvedSchedule(savedSchedule.type, savedSchedule.clockInTime, savedSchedule.clockOutTime)
        return createWorkdayResponse(memberId, date, schedule)
    }

    @Transactional(readOnly = true)
    fun getMonthlyEarnings(memberId: Long, year: Int, month: Int): MonthlyEarningsResponse {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        val today = LocalDate.now()
        val defaultSalary = earningsCalculator.getDefaultMonthlySalary(memberId, start) ?: 0

        val monthlyPolicy = resolveMonthlyRepresentativePolicy(memberId, year, month)

        val policyDailyMinutes = SalaryCalculator.calculateWorkMinutes(
            monthlyPolicy.clockInTime, monthlyPolicy.clockOutTime,
        )
        val workDaysInMonth = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .count { d -> monthlyPolicy.workdays.any { it.dayOfWeek == d.dayOfWeek } }
        val standardMinutes = policyDailyMinutes * workDaysInMonth

        if (start.isAfter(today)) {
            return MonthlyEarningsResponse(0, defaultSalary, 0, standardMinutes)
        }

        val lastCalculableDate = minOf(end, today)

        val savedSchedulesByDate = dailyWorkScheduleRepository
            .findAllByMemberIdAndDateBetween(memberId, start, lastCalculableDate)
            .associateBy { it.date }

        var totalEarnings = BigDecimal.ZERO
        var workedMinutes = 0L

        var date = start
        while (!date.isAfter(lastCalculableDate)) {
            val schedule = resolveScheduleForDate(savedSchedulesByDate[date], monthlyPolicy, date)

            if (schedule.type == DailyWorkScheduleType.WORK && schedule.clockIn != null && schedule.clockOut != null) {
                workedMinutes += SalaryCalculator.calculateWorkMinutes(schedule.clockIn, schedule.clockOut)
            } else if (schedule.type == DailyWorkScheduleType.VACATION) {
                workedMinutes += policyDailyMinutes
            }

            val dailyEarnings = earningsCalculator.calculateDailyEarnings(
                memberId, date, monthlyPolicy, schedule.type, schedule.clockIn, schedule.clockOut,
            )
            totalEarnings = totalEarnings.add(dailyEarnings ?: BigDecimal.ZERO)

            date = date.plusDays(1)
        }

        return MonthlyEarningsResponse(
            totalEarnings = totalEarnings.toInt(),
            defaultSalary = defaultSalary,
            workedMinutes = workedMinutes,
            standardMinutes = standardMinutes,
        )
    }

    private fun resolveScheduleForDate(
        saved: DailyWorkSchedule?,
        policy: WorkPolicyVersion,
        date: LocalDate,
    ): ResolvedSchedule {
        if (saved != null) {
            return ResolvedSchedule(saved.type, saved.clockInTime, saved.clockOutTime)
        }
        val isWorkday = policy.workdays.any { it.dayOfWeek == date.dayOfWeek }
        return if (isWorkday) {
            ResolvedSchedule(DailyWorkScheduleType.WORK, policy.clockInTime, policy.clockOutTime)
        } else {
            ResolvedSchedule(DailyWorkScheduleType.NONE, null, null)
        }
    }

    private fun createWorkdayResponse(
        memberId: Long,
        date: LocalDate,
        schedule: ResolvedSchedule,
    ): WorkdayResponse {
        if (schedule.type == DailyWorkScheduleType.NONE) {
            return WorkdayResponse(
                date = date,
                type = DailyWorkScheduleType.NONE,
                dailyPay = 0,
            )
        }
        val policy = resolveMonthlyRepresentativePolicy(memberId, date.year, date.monthValue)
        val earnings = earningsCalculator.calculateDailyEarnings(
            memberId, date, policy, schedule.type, schedule.clockIn, schedule.clockOut,
        )
        return WorkdayResponse(
            date = date,
            type = schedule.type,
            dailyPay = earnings?.toInt() ?: 0,
            clockInTime = schedule.clockIn,
            clockOutTime = schedule.clockOut,
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

private data class ResolvedSchedule(
    val type: DailyWorkScheduleType,
    val clockIn: LocalTime?,
    val clockOut: LocalTime?,
)
