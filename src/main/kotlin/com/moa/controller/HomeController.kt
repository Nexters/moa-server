package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.ProfileService
import com.moa.service.WorkPolicyService
import com.moa.service.WorkdayService
import com.moa.service.dto.HomeResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@Tag(name = "Home", description = "홈 화면 API")
@RestController
class HomeController(
    private val profileService: ProfileService,
    private val workdayService: WorkdayService,
    private val workPolicyService: WorkPolicyService,
) {

    @GetMapping("/api/v1/home")
    fun getHome(@Auth member: AuthMemberInfo): ApiResponse<HomeResponse> {
        val today = LocalDate.now()
        val profile = profileService.getProfile(member.id)
        val earnings = workdayService.getMonthlyEarnings(member.id, today.year, today.monthValue)
        val schedule = workdayService.getSchedule(member.id, today)
        val policy = workPolicyService.getCurrent(member.id)
        
        return ApiResponse.success(
            HomeResponse(
                workplace = profile.workplace,
                workedEarnings = earnings.workedEarnings,
                standardSalary = earnings.standardSalary,
                dailyPay = schedule.dailyPay,
                clockInTime = policy.clockInTime,
                clockOutTime = policy.clockOutTime,
            )
        )
    }
}
