package com.moa.repository

import com.moa.entity.AppVersion
import com.moa.entity.OsType
import org.springframework.data.jpa.repository.JpaRepository

interface AppVersionRepository : JpaRepository<AppVersion, Long> {
    fun findByOsType(osType: OsType): AppVersion?
}
