package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.AuthService
import com.moa.service.dto.*
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 API")
@RestController
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/api/v1/auth/kakao")
    fun kakaoSignInUp(@RequestBody @Valid kaKaoSignInUpRequest: KaKaoSignInUpRequest): ResponseEntity<ApiResponse<KakaoSignInUpResponse>> {
        return ResponseEntity.ok(ApiResponse.success(authService.kakaoSignInUp(kaKaoSignInUpRequest)))
    }

    @PostMapping("/api/v1/auth/apple")
    fun appleSignInUp(@RequestBody appleSignInUpRequest: AppleSignInUpRequest): ResponseEntity<ApiResponse<AppleSignInUpResponse>> {
        return ResponseEntity.ok(ApiResponse.success(authService.appleSignInUp(appleSignInUpRequest)))
    }

    @PostMapping("/api/v1/auth/logout")
    fun logout(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid request: LogoutRequest,
    ): ApiResponse<Unit> {
        authService.logout(member.id, request)
        return ApiResponse.success()
    }
}
