package com.moa.controller

import com.moa.common.response.ApiResponse
import com.moa.service.dto.DeployInfoResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@Tag(name = "Deploy Info", description = "배포 정보 API — Blue-Green 검증용")
@RestController
@RequestMapping("/api/v1/deploy-info")
class DeployInfoController(
    @Value("\${app.version:dev}") private val version: String,
    @Value("\${app.deploy-color:local}") private val color: String,
) {
    private val startedAt: Instant = Instant.now()

    @GetMapping
    fun deployInfo() =
        ApiResponse.success(DeployInfoResponse.of(version, color, startedAt))
}
