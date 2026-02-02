package com.moa.service

import com.moa.repository.*
import com.moa.service.dto.OnboardingStatusResponse
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
    private val workPolicyDayPolicyRepository: WorkPolicyDayPolicyRepository,
) {

    @Transactional(readOnly = true)
    fun getStatus(memberId: Long, today: LocalDate = LocalDate.now()): OnboardingStatusResponse {
        // 필수 약관 동의 여부
        val requiredCodes = termRepository.findAll()
            .filter { it.required }
            .map { it.code }
            .toSet()

        val agreements = termAgreementRepository.findAllByMemberId(memberId)
            .associate { it.termCode to it.agreed }

        val hasRequiredTermsAgreed = requiredCodes.all { agreements[it] == true }

        // 프로필 완료 여부
        val profile = profileRepository.findByMemberId(memberId)
        val profileCompleted =
            profile != null && profile.nickname.isNotBlank() && profile.workplaceName.isNotBlank()

        // 급여 완료 여부
        val payrollCompleted =
            payrollVersionRepository
                .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today) != null

        // 근무정책 완료 여부
        val version =
            workPolicyVersionRepository
                .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)

        val workPolicyCompleted =
            version != null && workPolicyDayPolicyRepository.existsByWorkPolicyVersionId(version.id)

        return OnboardingStatusResponse(
            hasRequiredTermsAgreed = hasRequiredTermsAgreed,
            profileCompleted = profileCompleted,
            payrollCompleted = payrollCompleted,
            workPolicyCompleted = workPolicyCompleted,
        )
    }
}
