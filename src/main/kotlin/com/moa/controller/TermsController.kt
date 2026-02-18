package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.TermsService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Terms", description = "이용약관 API")
@RestController
@RequestMapping("/api/v1/terms")
class TermsController(
    private val termsService: TermsService,
) {

    @GetMapping
    fun getTerms(@Auth member: AuthMemberInfo) =
        ApiResponse.success(termsService.getTerms())
}
