package com.moa.common.exception

enum class ErrorCode(
    val code: String,
    val message: String,
) {
    // Common
    INVALID_INPUT("C001", "잘못된 입력입니다"),
    INTERNAL_SERVER_ERROR("C002", "서버 내부 오류가 발생했습니다"),

    RESOURCE_NOT_FOUND("N001", "리소스를 찾을 수 없습니다"),

    UNAUTHORIZED("A001", "인증되지 않은 사용자입니다"),

    FORBIDDEN("F001", "권한이 없습니다"),

    UNPROCESSABLE_ENTITY("UE001", "처리할 수 없는 요구입니다"),
}
