package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.WorkPolicyService
import com.moa.service.dto.WorkPolicyUpsertRequest
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "WorkPolicy", description = "근무 정책 API")
@RestController
@RequestMapping("/api/v1/work-policy")
class WorkPolicyController(
    private val workPolicyService: WorkPolicyService,
) {

    @GetMapping
    fun getWorkPolicy(@Auth member: AuthMemberInfo) =
        ApiResponse.success(workPolicyService.getCurrent(member.id))

    @PatchMapping
    fun upsertWorkPolicy(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: WorkPolicyUpsertRequest,
    ) = ApiResponse.success(workPolicyService.upsert(member.id, req))
}
