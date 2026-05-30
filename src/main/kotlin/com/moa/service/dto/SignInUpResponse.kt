package com.moa.service.dto

data class SignInUpResponse(
    val userId: Long,
    val accessToken: String,
)
