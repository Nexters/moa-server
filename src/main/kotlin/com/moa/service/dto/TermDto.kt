package com.moa.service.dto

data class TermDto(
    val code: String,
    val title: String,
    val required: Boolean,
    val contentUrl: String,
)
