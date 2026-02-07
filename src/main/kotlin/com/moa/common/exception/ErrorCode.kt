package com.moa.common.exception

enum class ErrorCode(
    val code: String,
    val message: String,
) {
    // 5xx
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다"),

    // 4xx
    BAD_REQUEST("BAD_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED("UNAUTHORIZED", "인증되지 않은 사용자입니다"),
    ONBOARDING_INCOMPLETE("ONBOARDING_INCOMPLETE", "온보딩이 완료되지 않았습니다"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다"),

    INVALID_PAYROLL_INPUT("INVALID_PAYROLL_INPUT", "급여 입력값이 유효하지 않습니다"),
    INVALID_WORK_POLICY_INPUT("INVALID_WORK_POLICY_INPUT", "근무정책 입력값이 유효하지 않습니다"),
    REQUIRED_TERMS_MUST_BE_AGREED("REQUIRED_TERMS_MUST_BE_AGREED", "필수 약관은 동의해야 합니다"),

    INVALID_ID_TOKEN("INVALID_ID_TOKEN", "유효하지 않은 ID 토큰입니다"),
    INVALID_PROVIDER("INVALID_PROVIDER", "유효하지 않는 로그인 방식입니다."),
    EXPIRED_TOKEN("EXPIRED_TOKEN", "토큰이 만료되었습니다"),
    APPLE_TOKEN_EXCHANGE_FAILED("APPLE_TOKEN_EXCHANGE_FAILED", "Apple 토큰 교환에 실패했습니다"),
}
