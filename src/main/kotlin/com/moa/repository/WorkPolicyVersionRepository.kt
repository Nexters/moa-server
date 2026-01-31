package com.moa.repository

import com.moa.entity.WorkPolicyVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface WorkPolicyVersionRepository : JpaRepository<WorkPolicyVersion, Long> {
    fun findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId: Long,
        effectiveFrom: LocalDate,
    ): WorkPolicyVersion?

    fun findByMemberIdAndEffectiveFrom(memberId: Long, effectiveFrom: LocalDate): WorkPolicyVersion?
}
