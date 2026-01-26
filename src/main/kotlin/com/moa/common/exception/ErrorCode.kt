package com.moa.common.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "리소스를 찾을 수 없습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 내부 오류가 발생했습니다"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "C004", "입력값 검증에 실패했습니다"),

    // Entity
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "E001", "엔티티를 찾을 수 없습니다"),
}
