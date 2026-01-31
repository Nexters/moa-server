package com.moa.repository

import com.moa.entity.PayrollVersion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface PayrollVersionRepository : JpaRepository<PayrollVersion, Long> {
    fun findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
        memberId: Long,
        effectiveFrom: LocalDate,
    ): PayrollVersion?

    fun findByMemberIdAndEffectiveFrom(memberId: Long, effectiveFrom: LocalDate): PayrollVersion?
}
