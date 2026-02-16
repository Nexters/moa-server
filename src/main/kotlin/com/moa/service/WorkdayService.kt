package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.DailyWorkSchedule
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.DailyWorkScheduleRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.WorkdayEditRequest
import com.moa.service.dto.WorkdayResponse
import com.moa.service.dto.WorkdayUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

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
                date = date,
                clockInTime = workSchedule.clockInTime,
                clockOutTime = workSchedule.clockOutTime,
            )
        }

        val policy = findEffectivePolicyForWorkday(memberId, date)

        return WorkdayResponse(
            date = date,
            clockInTime = policy.clockInTime,
            clockOutTime = policy.clockOutTime,
        )
    }

    @Transactional
    fun upsertSchedule(memberId: Long, date: LocalDate, req: WorkdayUpsertRequest): WorkdayResponse {
        validateClockTimes(req.clockInTime, req.clockOutTime)

        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?.apply {
                this.clockInTime = req.clockInTime
                this.clockOutTime = req.clockOutTime
            }
            ?: DailyWorkSchedule(
                memberId = memberId,
                date = date,
                clockInTime = req.clockInTime,
                clockOutTime = req.clockOutTime,
            )

        val savedSchedule = dailyWorkScheduleRepository.save(workSchedule)

        return WorkdayResponse(
            date = date,
            clockInTime = savedSchedule.clockInTime,
            clockOutTime = savedSchedule.clockOutTime,
        )
    }

    @Transactional
    fun patchClockOut(memberId: Long, date: LocalDate, req: WorkdayEditRequest): WorkdayResponse {
        val workSchedule = dailyWorkScheduleRepository.findByMemberIdAndDate(memberId, date)
            ?: run {
                val policy = findEffectivePolicyForWorkday(memberId, date)
                DailyWorkSchedule(
                    memberId = memberId,
                    date = date,
                    clockInTime = policy.clockInTime,
                    clockOutTime = policy.clockOutTime,
                )
            }

        workSchedule.apply {
            validateClockTimes(this.clockInTime, req.clockOutTime)
            this.clockOutTime = req.clockOutTime
        }

        val saved = dailyWorkScheduleRepository.save(workSchedule)

        return WorkdayResponse(
            date = saved.date,
            clockInTime = saved.clockInTime,
            clockOutTime = saved.clockOutTime,
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

    private fun validateClockTimes(clockInTime: LocalTime, clockOutTime: LocalTime) {
        if (!clockInTime.isBefore(clockOutTime)) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }
    }
}
