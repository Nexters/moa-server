package com.moa.common.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "admin")
data class AdminProperties(
    val apiKey: String,
)
