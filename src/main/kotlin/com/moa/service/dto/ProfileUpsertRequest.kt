package com.moa.service.dto

data class ProfileUpsertRequest(
    val nickname: String,
    val workplace: WorkplaceDto,
)
