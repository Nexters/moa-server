package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.WorkdayService
import com.moa.service.dto.WorkdayEditRequest
import com.moa.service.dto.WorkdayUpsertRequest
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@Tag(name = "Workday", description = "근무일/스케줄 API (출퇴근 일정 조회·등록·수정)")
@RestController
@RequestMapping("/api/v1/workdays")
class WorkdayController(
    private val workdayService: WorkdayService,
) {

    @GetMapping("/{date}")
    fun getSchedule(
        @Auth member: AuthMemberInfo,
        @PathVariable date: LocalDate,
    ) = ApiResponse.success(workdayService.getSchedule(member.id, date))

    @GetMapping("/earnings")
    fun getMonthlyEarnings(
        @Auth member: AuthMemberInfo,
        @RequestParam year: Int,
        @RequestParam month: Int,
    ) = ApiResponse.success(workdayService.getMonthlyEarnings(member.id, year, month))

    @GetMapping
    fun getMonthlySchedules(
        @Auth member: AuthMemberInfo,
        @RequestParam year: Int,
        @RequestParam month: Int,
    ) = ApiResponse.success(
        workdayService.getMonthlySchedules(member.id, year, month)
    )
    
    @PutMapping("/{date}")
    fun upsertSchedule(
        @Auth member: AuthMemberInfo,
        @PathVariable date: LocalDate,
        @RequestBody @Valid req: WorkdayUpsertRequest,
    ) = ApiResponse.success(workdayService.upsertSchedule(member.id, date, req))

    @PatchMapping("/{date}")
    fun patchClockOut(
        @Auth member: AuthMemberInfo,
        @PathVariable date: LocalDate,
        @RequestBody @Valid req: WorkdayEditRequest,
    ) = ApiResponse.success(workdayService.patchClockOut(member.id, date, req))
}
