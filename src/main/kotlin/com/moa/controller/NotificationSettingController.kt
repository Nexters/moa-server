package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.dto.NotificationSettingUpdateRequest
import com.moa.service.notification.NotificationSettingService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "NotificationSetting", description = "알림 설정 API (근무/급여일/프로모션 알림)")
@RestController
@RequestMapping("/api/v1/settings/notification")
class NotificationSettingController(
    private val notificationSettingService: NotificationSettingService,
) {

    @GetMapping
    fun getSettings(
        @Auth member: AuthMemberInfo,
    ) = ApiResponse.success(notificationSettingService.getSettings(member.id))

    @PatchMapping
    fun updateSetting(
        @Auth member: AuthMemberInfo,
        @RequestBody req: NotificationSettingUpdateRequest,
    ) = ApiResponse.success(notificationSettingService.updateSetting(member.id, req.type, req.checked))
}
