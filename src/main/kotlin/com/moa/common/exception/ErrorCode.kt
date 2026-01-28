package com.moa.common.exception

enum class ErrorCode(
    val code: String,
    val message: String,
) {
    // Common
    INVALID_INPUT("INVALID_INPUT", "잘못된 입력입니다"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"),

    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다"),

    UNAUTHORIZED("UNAUTHORIZED", "인증되지 않은 사용자입니다"),

    FORBIDDEN("FORBIDDEN", "권한이 없습니다"),

    BAD_REQUEST("BAD_REQUEST", "잘못된 요청입니다."),
}
