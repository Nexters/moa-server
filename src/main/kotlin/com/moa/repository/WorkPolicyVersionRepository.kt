package com.moa.repository

import com.moa.entity.WorkPolicyVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface WorkPolicyVersionRepository : JpaRepository<WorkPolicyVersion, Long> {
    fun findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId: Long,
        effectiveFrom: LocalDate,
    ): WorkPolicyVersion?

    fun findByMemberIdAndEffectiveFrom(memberId: Long, effectiveFrom: LocalDate): WorkPolicyVersion?

    @Query(
        """
        select distinct w from WorkPolicyVersion w
        join fetch w.workdays
        where w.effectiveFrom = (
            select max(w2.effectiveFrom)
            from WorkPolicyVersion w2
            where w2.memberId = w.memberId and w2.effectiveFrom <= :date
        )
    """
    )
    fun findLatestEffectivePoliciesPerMember(date: LocalDate): List<WorkPolicyVersion>
}
