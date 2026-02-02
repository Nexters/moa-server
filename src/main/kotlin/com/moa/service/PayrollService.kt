package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.entity.PayrollVersion
import com.moa.repository.PayrollVersionRepository
import com.moa.service.dto.PayrollResponse
import com.moa.service.dto.PayrollUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PayrollService(
    private val payrollVersionRepository: PayrollVersionRepository,
) {

    @Transactional
    fun upsert(memberId: Long, req: PayrollUpsertRequest): PayrollResponse {

        val effectiveFrom = req.effectiveFrom
        val salaryInputType = req.salaryInputType
        val salaryAmount = req.salaryAmount
        val paydayDay = req.paydayDay ?: 25

        if (paydayDay !in 1..31) {
            throw BadRequestException(ErrorCode.INVALID_PAYROLL_INPUT)
        }

        val saved = payrollVersionRepository.findByMemberIdAndEffectiveFrom(memberId, effectiveFrom)
            ?.apply {
                this.salaryInputType = salaryInputType
                this.salaryAmount = salaryAmount
                this.paydayDay = paydayDay
            }
            ?: payrollVersionRepository.save(
                PayrollVersion(
                    memberId = memberId,
                    effectiveFrom = effectiveFrom,
                    salaryInputType = salaryInputType,
                    salaryAmount = salaryAmount,
                    paydayDay = paydayDay,
                )
            )

        return PayrollResponse(
            effectiveFrom = saved.effectiveFrom,
            salaryInputType = saved.salaryInputType,
            salaryAmount = saved.salaryAmount,
            paydayDay = saved.paydayDay,
        )
    }
}
