package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.PayrollVersion
import com.moa.repository.PayrollVersionRepository
import com.moa.service.dto.PayrollResponse
import com.moa.service.dto.OnboardingPayrollUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PayrollService(
    private val payrollVersionRepository: PayrollVersionRepository,
) {

    @Transactional
    fun upsert(memberId: Long, req: OnboardingPayrollUpsertRequest, today: LocalDate = LocalDate.now()): PayrollResponse {
        val saved = payrollVersionRepository.findByMemberIdAndEffectiveFrom(memberId, today)
            ?.apply {
                this.salaryInputType = req.salaryInputType
                this.salaryAmount = req.salaryAmount
            }
            ?: payrollVersionRepository.save(
                PayrollVersion(
                    memberId = memberId,
                    effectiveFrom = today,
                    salaryInputType = req.salaryInputType,
                    salaryAmount = req.salaryAmount,
                )
            )

        return PayrollResponse(
            effectiveFrom = saved.effectiveFrom,
            salaryInputType = saved.salaryInputType,
            salaryAmount = saved.salaryAmount,
        )
    }

    @Transactional(readOnly = true)
    fun getCurrent(memberId: Long, today: LocalDate = LocalDate.now()): PayrollResponse {
        val version = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?: throw NotFoundException()
        return PayrollResponse(
            effectiveFrom = version.effectiveFrom,
            salaryInputType = version.salaryInputType,
            salaryAmount = version.salaryAmount,
        )
    }
}
