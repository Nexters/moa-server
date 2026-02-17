package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.MemberService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService,
) {

    @GetMapping
    fun getMember(@Auth member: AuthMemberInfo) =
        ApiResponse.success(memberService.getMember(member.id))
}
