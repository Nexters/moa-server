package com.moa.controller

import com.moa.common.auth.AdminAuth
import com.moa.common.response.ApiResponse
import com.moa.service.PublicHolidayService
import com.moa.service.dto.PublicHolidayCreateRequest
import com.moa.service.dto.PublicHolidayResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.*

@Tag(name = "Public Holiday", description = "공휴일 관리 API")
@RestController
@RequestMapping("/api/v1/admin/public-holidays")
class PublicHolidayController(
    private val publicHolidayService: PublicHolidayService,
) {

    @AdminAuth
    @SecurityRequirement(name = "AdminKey")
    @GetMapping
    fun getByYear(@RequestParam year: Int) =
        ApiResponse.success(
            publicHolidayService.getByYear(year).map { PublicHolidayResponse.from(it) }
        )

    @AdminAuth
    @SecurityRequirement(name = "AdminKey")
    @PostMapping
    fun create(@RequestBody request: PublicHolidayCreateRequest) =
        ApiResponse.success(
            PublicHolidayResponse.from(publicHolidayService.create(request.date, request.name))
        )

    @AdminAuth
    @SecurityRequirement(name = "AdminKey")
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) =
        ApiResponse.success(publicHolidayService.delete(id))
}
