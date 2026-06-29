package com.moa.service.dto

data class TokenRefreshResponse(
    val accessToken: String,
    val refreshToken: String,
)
