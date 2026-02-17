package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.PayrollService
import com.moa.service.dto.OnboardingPayrollUpsertRequest
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "Payroll", description = "급여 정보 API")
@RestController
@RequestMapping("/api/v1/payroll")
class PayrollController(
    private val payrollService: PayrollService,
) {

    @GetMapping
    fun getPayroll(@Auth member: AuthMemberInfo) =
        ApiResponse.success(payrollService.getCurrent(member.id))

    @PatchMapping
    fun upsertPayroll(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: OnboardingPayrollUpsertRequest,
    ) = ApiResponse.success(payrollService.upsert(member.id, req))
}
