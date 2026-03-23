package com.moa.controller

import com.moa.common.auth.AdminAuth
import com.moa.common.response.ApiResponse
import com.moa.entity.OsType
import com.moa.service.AppVersionService
import com.moa.service.dto.AppVersionUpdateRequest
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Version", description = "앱 버전 API")
@RestController
@RequestMapping("/api/v1/version")
class VersionController(
    private val appVersionService: AppVersionService,
) {

    @GetMapping
    fun getVersion(@RequestParam osType: OsType) =
        ApiResponse.success(appVersionService.getVersion(osType))

    @AdminAuth
    @SecurityRequirement(name = "AdminKey")
    @PutMapping
    fun updateVersion(@RequestBody request: AppVersionUpdateRequest) =
        ApiResponse.success(appVersionService.updateVersion(request))
}
