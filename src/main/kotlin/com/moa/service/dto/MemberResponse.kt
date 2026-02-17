package com.moa.service.dto

import com.moa.entity.ProviderType

data class MemberResponse(
    val id: Long,
    val provider: ProviderType,
)
