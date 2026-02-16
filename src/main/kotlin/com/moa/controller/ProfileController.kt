package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.ProfileService
import com.moa.service.dto.NicknameUpdateRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/profile")
class ProfileController(
    private val profileService: ProfileService,
) {

    @PatchMapping("/nickname")
    fun updateNickname(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid req: NicknameUpdateRequest,
    ) = ApiResponse.success(profileService.updateNickname(member.id, req))
}
