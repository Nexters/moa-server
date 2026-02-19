package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.FcmTokenService
import com.moa.service.dto.FcmTokenRequest
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "fcm", description = "FCM TOKEN 갱신 및 삭제")
@RestController
@RequestMapping("/api/v1/fcm/token")
class FcmTokenController(
    private val fcmTokenService: FcmTokenService,
) {

    @PutMapping
    fun registerToken(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid request: FcmTokenRequest,
    ) = ApiResponse.success(fcmTokenService.registerToken(member.id, request.token))

    @DeleteMapping
    fun deleteToken(
        @Auth member: AuthMemberInfo,
        @RequestBody @Valid request: FcmTokenRequest,
    ) = ApiResponse.success(fcmTokenService.deleteToken(member.id, request.token))
}
