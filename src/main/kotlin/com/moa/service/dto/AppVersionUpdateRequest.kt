package com.moa.service.dto

import com.moa.entity.OsType

data class AppVersionUpdateRequest(
    val osType: OsType,
    val latestVersion: String,
    val minimumVersion: String,
)
