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
    
    @Query("""
        SELECT DISTINCT w FROM WorkPolicyVersion w
        JOIN FETCH w.workdays
        WHERE w.effectiveFrom = (
            SELECT MAX(w2.effectiveFrom)
            FROM WorkPolicyVersion w2
            WHERE w2.memberId = w.memberId AND w2.effectiveFrom <= :date
        )
    """)
    fun findLatestEffectivePoliciesPerMember(date: LocalDate): List<WorkPolicyVersion>
}
