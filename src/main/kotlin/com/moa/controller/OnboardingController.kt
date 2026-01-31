package com.moa.controller

import com.moa.common.response.ApiResponse
import com.moa.service.OnboardingStatusService
import com.moa.service.PayrollService
import com.moa.service.ProfileService
import com.moa.service.TermsService
import com.moa.service.WorkPolicyService
import com.moa.service.dto.PayrollUpsertRequest
import com.moa.service.dto.ProfileUpsertRequest
import com.moa.service.dto.TermsAgreementRequest
import com.moa.service.dto.WorkPolicyUpsertRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/onboarding")
class OnboardingController(
    private val onboardingStatusService: OnboardingStatusService,
    private val profileService: ProfileService,
    private val payrollService: PayrollService,
    private val workPolicyService: WorkPolicyService,
    private val termsService: TermsService,
) {

    @GetMapping("/status")
    fun status() =
        ApiResponse.success(onboardingStatusService.getStatus())

    @PatchMapping("/profile")
    fun upsertProfile(@RequestBody @Valid req: ProfileUpsertRequest) =
        ApiResponse.success(profileService.upsertProfile(req))

    @PatchMapping("/payroll")
    fun upsertPayroll(@RequestBody @Valid req: PayrollUpsertRequest) =
        ApiResponse.success(payrollService.upsert(req))

    @PatchMapping("/work-policy")
    fun upsertWorkPolicy(@RequestBody @Valid req: WorkPolicyUpsertRequest) =
        ApiResponse.success(workPolicyService.upsert(req))

    @GetMapping("/terms")
    fun terms() =
        ApiResponse.success(termsService.getTerms())

    @GetMapping("/terms/agreements")
    fun agreements() =
        ApiResponse.success(termsService.getAgreements())

    @PutMapping("/terms/agreements")
    fun agree(@RequestBody @Valid req: TermsAgreementRequest) =
        ApiResponse.success(termsService.upsertAgreements(req))
}
