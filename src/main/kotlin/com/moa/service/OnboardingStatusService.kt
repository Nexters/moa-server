package com.moa.service

import com.moa.repository.*
import com.moa.service.dto.OnboardingStatusResponse
import com.moa.service.dto.PayrollResponse
import com.moa.service.dto.ProfileResponse
import com.moa.service.dto.WorkPolicyResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class OnboardingStatusService(
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
    private val profileRepository: ProfileRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
) {

    @Transactional(readOnly = true)
    fun getStatus(memberId: Long, today: LocalDate = LocalDate.now()): OnboardingStatusResponse {
        // 프로필 완료 여부
        val profile = profileRepository.findByMemberId(memberId)
            ?.takeIf { it.nickname.isNotBlank() && it.workplace.isNotBlank() }
            ?.let {
                ProfileResponse(
                    nickname = it.nickname,
                    workplace = it.workplace,
                )
            }

        // 급여 완료 여부
        val payroll = payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?.let {
                PayrollResponse(
                    effectiveFrom = it.effectiveFrom,
                    salaryInputType = it.salaryInputType,
                    salaryAmount = it.salaryAmount,
                    paydayDay = it.paydayDay,
                )
            }

        // 근무정책 완료 여부
        val workPolicy = workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?.takeIf { it.workdays.isNotEmpty() }
            ?.let {
                WorkPolicyResponse(
                    effectiveFrom = it.effectiveFrom,
                    workdays = it.workdays.sorted(),
                    clockInTime = it.clockInTime,
                    clockOutTime = it.clockOutTime,
                    breakStartTime = it.breakStartTime,
                    breakEndTime = it.breakEndTime,
                )
            }

        // 필수 약관 동의 여부
        val requiredCodes = termRepository.findAll()
            .filter { it.required }
            .map { it.code }
            .toSet()

        val agreements = termAgreementRepository.findAllByMemberId(memberId)
            .associate { it.termCode to it.agreed }

        val hasRequiredTermsAgreed = requiredCodes.all { agreements[it] == true }

        return OnboardingStatusResponse(
            profile = profile,
            payroll = payroll,
            workPolicy = workPolicy,
            hasRequiredTermsAgreed = hasRequiredTermsAgreed,
        )
    }
}
