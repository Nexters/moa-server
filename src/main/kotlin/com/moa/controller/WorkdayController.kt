package com.moa.controller

import com.moa.common.auth.Auth
import com.moa.common.auth.AuthMemberInfo
import com.moa.common.response.ApiResponse
import com.moa.service.WorkdayService
import com.moa.service.dto.WorkdayEditRequest
import com.moa.service.dto.WorkdayUpsertRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

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
