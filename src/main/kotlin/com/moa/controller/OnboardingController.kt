package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthenticatedMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.*
import com.moa.service.dto.PayrollUpsertRequest
import com.moa.service.dto.ProfileUpsertRequest
import com.moa.service.dto.TermsAgreementRequest
import com.moa.service.dto.WorkPolicyUpsertRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    private val onboardingStatusService: OnboardingStatusService,
    private val profileService: ProfileService,
    private val payrollService: PayrollService,
    private val workPolicyService: WorkPolicyService,
    private val termsService: TermsService,
) {

    @GetMapping("/status")
    fun status(@Auth member: AuthenticatedMemberInfo) =
        ApiResponse.success(onboardingStatusService.getStatus(member.id))

    @PatchMapping("/profile")
    fun upsertProfile(
        @Auth member: AuthenticatedMemberInfo,
        @RequestBody @Valid req: ProfileUpsertRequest,
    ) = ApiResponse.success(profileService.upsertProfile(member.id, req))

    @PatchMapping("/payroll")
    fun upsertPayroll(
        @Auth member: AuthenticatedMemberInfo,
        @RequestBody @Valid req: PayrollUpsertRequest,
    ) = ApiResponse.success(payrollService.upsert(member.id, req))

    @PatchMapping("/work-policy")
    fun upsertWorkPolicy(
        @Auth member: AuthenticatedMemberInfo,
        @RequestBody @Valid req: WorkPolicyUpsertRequest,
    ) = ApiResponse.success(workPolicyService.upsert(member.id, req))

    @GetMapping("/terms")
    fun terms() =
        ApiResponse.success(termsService.getTerms())

    @GetMapping("/terms/agreements")
    fun agreements(@Auth member: AuthenticatedMemberInfo) =
        ApiResponse.success(termsService.getAgreements(member.id))

    @PutMapping("/terms/agreements")
    fun agree(
        @Auth member: AuthenticatedMemberInfo,
        @RequestBody @Valid req: TermsAgreementRequest,
    ) = ApiResponse.success(termsService.upsertAgreements(member.id, req))
}
