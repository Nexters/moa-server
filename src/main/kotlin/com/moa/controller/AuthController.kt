package com.moa.controller

import com.moa.common.response.ApiResponse
import com.moa.service.auth.AuthService
import com.moa.service.dto.KaKaoSignInUpRequest
import com.moa.service.dto.KakaoSignInUpResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/api/v1/auth/kakao")
    fun kakaoSignInUp(@RequestBody kaKaoSignInUpRequest: KaKaoSignInUpRequest): ResponseEntity<ApiResponse<KakaoSignInUpResponse>> {
        val response = authService.kakaoSignInUp(kaKaoSignInUpRequest)
        return ResponseEntity.ok(ApiResponse.Companion.success(response))
    }
}
