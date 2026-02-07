package com.moa.controller

import com.moa.common.response.ApiResponse
import com.moa.service.AuthService
import com.moa.service.dto.AppleSignInUpRequest
import com.moa.service.dto.AppleSignInUpResponse
import com.moa.service.dto.KaKaoSignInUpRequest
import com.moa.service.dto.KakaoSignInUpResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

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
}
