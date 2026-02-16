package com.moa.service

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.NotFoundException
import com.moa.entity.PayrollVersion
import com.moa.repository.PayrollVersionRepository
import com.moa.service.dto.PayrollResponse
import com.moa.service.dto.PayrollUpsertRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class PayrollService(
    private val payrollVersionRepository: PayrollVersionRepository,
) {

    @Transactional
    fun upsert(memberId: Long, req: PayrollUpsertRequest, today: LocalDate = LocalDate.now()): PayrollResponse {
        val salaryInputType = req.salaryInputType
        val salaryAmount = req.salaryAmount
        val paydayDay = req.paydayDay ?: 25

        if (paydayDay !in 1..31) {
            throw BadRequestException(ErrorCode.INVALID_PAYROLL_INPUT)
        }

        val saved = payrollVersionRepository.findByMemberIdAndEffectiveFrom(memberId, today)
            ?.apply {
                this.salaryInputType = salaryInputType
                this.salaryAmount = salaryAmount
                this.paydayDay = paydayDay
            }
            ?: payrollVersionRepository.save(
                PayrollVersion(
                    memberId = memberId,
                    effectiveFrom = today,
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

    @Transactional(readOnly = true)
    fun getCurrent(memberId: Long, today: LocalDate = LocalDate.now()): PayrollResponse {
        val version = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?: throw NotFoundException()
        return PayrollResponse(
            effectiveFrom = version.effectiveFrom,
            salaryInputType = version.salaryInputType,
            salaryAmount = version.salaryAmount,
            paydayDay = version.paydayDay,
        )
    }
}
