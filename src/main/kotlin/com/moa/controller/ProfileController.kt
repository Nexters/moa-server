package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.ProfileService
import com.moa.service.dto.NicknameUpdateRequest
import com.moa.service.dto.WorkplaceUpdateRequest
import com.moa.service.dto.PaydayUpdateRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profile")
class ProfileController(
    private val profileService: ProfileService,
) {

    @GetMapping
    fun getProfile(@Auth member: AuthMemberInfo) =
        ApiResponse.success(profileService.getProfile(member.id))

    @PatchMapping("/nickname")
    fun updateNickname(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: NicknameUpdateRequest,
    ) = ApiResponse.success(profileService.updateNickname(member.id, req))

    @PatchMapping("/workplace")
    fun updateWorkplace(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: WorkplaceUpdateRequest,
    ) = ApiResponse.success(profileService.updateWorkplace(member.id, req))

    @PatchMapping("/payday")
    fun updatePayday(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: PaydayUpdateRequest,
    ) = ApiResponse.success(profileService.updatePayday(member.id, req))
}
