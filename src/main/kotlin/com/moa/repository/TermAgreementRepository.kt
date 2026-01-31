package com.moa.repository

import com.moa.entity.TermAgreement
import org.springframework.data.jpa.repository.JpaRepository

interface TermAgreementRepository : JpaRepository<TermAgreement, Long> {
    fun findAllByMemberId(memberId: Long): List<TermAgreement>
    
    fun findByMemberIdAndTermCode(memberId: Long, termCode: String): TermAgreement?
}
