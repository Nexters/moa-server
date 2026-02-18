package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.WorkPolicyVersion
import com.moa.repository.WorkPolicyVersionRepository
import com.moa.service.dto.WorkPolicyResponse
import com.moa.service.dto.WorkPolicyUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class WorkPolicyService(
    private val versionRepository: WorkPolicyVersionRepository,
) {

    @Transactional
    fun upsert(memberId: Long, req: WorkPolicyUpsertRequest, today: LocalDate = LocalDate.now()): WorkPolicyResponse {
        val version = versionRepository.findByMemberIdAndEffectiveFrom(memberId, today)
            ?: WorkPolicyVersion(
                memberId = memberId,
                effectiveFrom = today,
                clockInTime = req.clockInTime,
                clockOutTime = req.clockOutTime,
                workdays = req.workdays.toMutableSet(),
            )

        version.clockInTime = req.clockInTime
        version.clockOutTime = req.clockOutTime
        version.workdays = req.workdays.toMutableSet()
        versionRepository.save(version)

        return WorkPolicyResponse(
            workdays = req.workdays.sortedBy { it.dayOfWeek.value },
            clockInTime = req.clockInTime,
            clockOutTime = req.clockOutTime,
        )
    }

    @Transactional(readOnly = true)
    fun getCurrent(memberId: Long, today: LocalDate = LocalDate.now()): WorkPolicyResponse {
        val version = versionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?: throw NotFoundException()

        return WorkPolicyResponse(
            workdays = version.workdays.sortedBy { it.dayOfWeek.value },
            clockInTime = version.clockInTime,
            clockOutTime = version.clockOutTime,
        )
    }
}
