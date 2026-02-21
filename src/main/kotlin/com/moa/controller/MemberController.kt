package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.MemberService
import com.moa.service.dto.WithdrawalRequest
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "Member", description = "회원 정보 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService,
) {

    @GetMapping
    fun getMember(@Auth member: AuthMemberInfo) =
        ApiResponse.success(memberService.getMember(member.id))

    @DeleteMapping
    fun deleteMember(
        @Auth member: AuthMemberInfo,
        @RequestBody req: WithdrawalRequest,
    ) =
        ApiResponse.success(memberService.deleteMember(member.id, req))

}
