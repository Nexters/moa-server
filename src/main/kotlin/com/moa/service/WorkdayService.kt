package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkSchedule
import com.moa.entity.DailyWorkScheduleType
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.WorkPolicyVersionRepository
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
) {

    @Transactional(readOnly = true)
    fun getSchedule(memberId: Long, date: LocalDate): WorkdayResponse {
        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
        if (workSchedule != null) {
            return WorkdayResponse(
                date = workSchedule.date,
                type = workSchedule.type,
                clockInTime = workSchedule.clockInTime,
                clockOutTime = workSchedule.clockOutTime,
            )
        }

        val policy = findEffectivePolicyForWorkday(memberId, date)

        return WorkdayResponse(
            date = date,
            type = DailyWorkScheduleType.WORK,
            clockInTime = policy.clockInTime,
            clockOutTime = policy.clockOutTime,
        )
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

        return WorkdayResponse(
            date = savedSchedule.date,
            type = savedSchedule.type,
            clockInTime = savedSchedule.clockInTime,
            clockOutTime = savedSchedule.clockOutTime,
        )
    }

    private fun findEffectivePolicyForWorkday(memberId: Long, date: LocalDate): WorkPolicyVersion {
        val policy = workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, date)
            ?: throw NotFoundException()

        if (policy.workdays.none { it.dayOfWeek == date.dayOfWeek }) {
            throw NotFoundException()
        }

        return policy
    }
}
