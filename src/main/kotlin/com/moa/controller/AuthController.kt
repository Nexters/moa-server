package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.AppleDesktopAuthService
import com.moa.service.AuthService
import com.moa.service.dto.*
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@Tag(name = "Auth", description = "인증 API")
@RestController
class AuthController(
    private val authService: AuthService,
    private val appleDesktopAuthService: AppleDesktopAuthService,
) {

    @PostMapping("/api/v1/auth/kakao")
    fun kakaoSignInUp(@RequestBody @Valid kaKaoSignInUpRequest: KaKaoSignInUpRequest): ResponseEntity<ApiResponse<SignInUpResponse>> {
        return ResponseEntity.ok(ApiResponse.success(authService.kakaoSignInUp(kaKaoSignInUpRequest)))
    }

    @PostMapping("/api/v1/auth/apple")
    fun appleSignInUp(@RequestBody @Valid appleSignInUpRequest: AppleSignInUpRequest): ResponseEntity<ApiResponse<SignInUpResponse>> {
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

    @PostMapping("/api/v1/auth/refresh")
    fun refresh(
        @RequestBody @Valid request: TokenRefreshRequest,
    ): ResponseEntity<ApiResponse<TokenRefreshResponse>> {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)))
    }

    // GET = 기본(scope 미요청). POST = Apple 이 scope(name/email) 요청 시 강제하는 response_mode=form_post 대비.
    @RequestMapping(
        "/api/v1/auth/apple/desktop/callback",
        method = [RequestMethod.GET, RequestMethod.POST],
    )
    fun appleCallback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        val location = appleDesktopAuthService.callback(code, state, error)
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build()
    }

    @PostMapping("/api/v1/auth/apple/desktop/complete")
    fun appleDesktopComplete(
        @RequestBody @Valid request: DesktopCompleteRequest,
    ): ResponseEntity<ApiResponse<SignInUpResponse>> =
        ResponseEntity.ok(ApiResponse.success(appleDesktopAuthService.complete(request.exchangeCode)))
}
