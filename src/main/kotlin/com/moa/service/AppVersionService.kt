package com.moa.service

import com.moa.common.exception.NotFoundException
import com.moa.entity.OsType
import com.moa.repository.AppVersionRepository
import com.moa.service.dto.AppVersionResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppVersionService(
    private val appVersionRepository: AppVersionRepository,
) {

    @Transactional(readOnly = true)
    fun getVersion(osType: OsType): AppVersionResponse {
        val version = appVersionRepository.findByOsType(osType)
            ?: throw NotFoundException()
        return AppVersionResponse(
            latestVersion = version.latestVersion,
            minimumVersion = version.minimumVersion,
        )
    }
}
