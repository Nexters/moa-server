package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.WorkPolicyResponse
import com.moa.service.dto.WorkPolicyUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WorkPolicyService(
    private val versionRepository: WorkPolicyVersionRepository,
) {

    @Transactional
    fun upsert(memberId: Long, req: WorkPolicyUpsertRequest): WorkPolicyResponse {

        validateRequest(req)

        val version = versionRepository.findByMemberIdAndEffectiveFrom(memberId, req.effectiveFrom)
            ?: WorkPolicyVersion(
                memberId = memberId,
                effectiveFrom = req.effectiveFrom,
                clockInTime = req.clockInTime,
                clockOutTime = req.clockOutTime,
                breakStartTime = req.breakStartTime,
                breakEndTime = req.breakEndTime,
                workdays = req.workdays.toMutableSet(),
            )

        version.clockInTime = req.clockInTime
        version.clockOutTime = req.clockOutTime
        version.breakStartTime = req.breakStartTime
        version.breakEndTime = req.breakEndTime
        version.workdays = req.workdays.toMutableSet()
        versionRepository.save(version)

        return WorkPolicyResponse(
            effectiveFrom = version.effectiveFrom,
            workdays = req.workdays.sortedBy { it.dayOfWeek.value },
            clockInTime = req.clockInTime,
            clockOutTime = req.clockOutTime,
            breakStartTime = req.breakStartTime,
            breakEndTime = req.breakEndTime,
        )
    }

    private fun validateRequest(req: WorkPolicyUpsertRequest) {
        if (!req.clockInTime.isBefore(req.clockOutTime)) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }
        if (!req.breakStartTime.isBefore(req.breakEndTime)) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }
        if (req.breakStartTime.isBefore(req.clockInTime) || req.breakEndTime.isAfter(req.clockOutTime)) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }
        if (req.workdays.isEmpty()) {
            throw BadRequestException(ErrorCode.INVALID_WORK_POLICY_INPUT)
        }
    }
}
