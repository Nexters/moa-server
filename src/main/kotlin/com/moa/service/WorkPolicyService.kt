package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.WorkPolicyDayPolicy
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.WorkPolicyDayPolicyRepository
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.DayPolicyDto
import com.moa.service.dto.DayPolicyResponse
import com.moa.service.dto.WorkPolicyResponse
import com.moa.service.dto.WorkPolicyUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkPolicyService(
    private val versionRepository: WorkPolicyVersionRepository,
    private val dayPolicyRepository: WorkPolicyDayPolicyRepository,
) {

    // TODO. 로그인/Member 연동 후 memberId 가져오도록 변경
    private fun currentMemberId(): Long = 1L

    @Transactional
    fun upsert(req: WorkPolicyUpsertRequest): WorkPolicyResponse {
        val memberId = currentMemberId()

        validateDays(req.days)

        val version = versionRepository.findByMemberIdAndEffectiveFrom(memberId, req.effectiveFrom)
            ?: versionRepository.save(
                WorkPolicyVersion(
                    memberId = memberId,
                    effectiveFrom = req.effectiveFrom,
                )
            )

        dayPolicyRepository.deleteAllByWorkPolicyVersionId(version.id)

        val rows = req.days.map { d ->
            WorkPolicyDayPolicy(
                workPolicyVersionId = version.id,
                workday = d.workday,
                clockInTime = d.clockInTime,
                clockOutTime = d.clockOutTime,
                breakStartTime = d.breakStartTime,
                breakEndTime = d.breakEndTime,
            )
        }
        dayPolicyRepository.saveAll(rows)

        return WorkPolicyResponse(
            effectiveFrom = version.effectiveFrom,
            days = req.days
                .sortedBy { it.workday.dayOfWeek.value }
                .map { it.toResponse() }
        )
    }

    private fun validateDays(days: List<DayPolicyDto>) {
        val distinct = days.map { it.workday }.toSet()
        if (distinct.size != days.size) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }

        days.forEach { d ->
            if (!d.clockInTime.isBefore(d.clockOutTime)) {
                throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
            }
            if (!d.breakStartTime.isBefore(d.breakEndTime)) {
                throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
            }

            if (d.breakStartTime.isBefore(d.clockInTime) || d.breakEndTime.isAfter(d.clockOutTime)) {
                throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
            }
        }
    }

    private fun DayPolicyDto.toResponse() = DayPolicyResponse(
        workday = workday,
        clockInTime = clockInTime,
        clockOutTime = clockOutTime,
        breakStartTime = breakStartTime,
        breakEndTime = breakEndTime,
    )
}
