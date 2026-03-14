package com.moa.controller.screen

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.WorkdayService
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Calendar", description = "캘린더 화면 API")
@RestController
@RequestMapping("/api/v1/calendar")
class CalendarController(
    private val workdayService: WorkdayService,
) {

    @GetMapping
    fun getCalendar(
        @Auth member: AuthMemberInfo,
        @RequestParam year: Int,
        @RequestParam month: Int,
    ) = ApiResponse.success(workdayService.getCalendar(member.id, year, month))
}
