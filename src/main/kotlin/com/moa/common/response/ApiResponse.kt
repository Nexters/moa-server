package com.moa.common.response

import com.moa.common.exception.ErrorCode

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val content: T?,
) {
    companion object {
        private const val SUCCESS_CODE = "SUCCESS"
        private const val SUCCESS_MESSAGE = "요청이 성공적으로 처리되었습니다"

        fun success(): ApiResponse<Unit> {
            return ApiResponse(
                code = SUCCESS_CODE,
                message = SUCCESS_MESSAGE,
                content = null,
            )
        }

        fun <T> success(content: T): ApiResponse<T> {
            return ApiResponse(
                code = SUCCESS_CODE,
                message = SUCCESS_MESSAGE,
                content = content,
            )
        }

        fun <T> success(content: T, message: String): ApiResponse<T> {
            return ApiResponse(
                code = SUCCESS_CODE,
                message = message,
                content = content,
            )
        }

        fun error(errorCode: ErrorCode): ApiResponse<Unit> {
            return ApiResponse(
                code = errorCode.code,
                message = errorCode.message,
                content = null,
            )
        }

        fun validationError(errors: List<FieldError>): ApiResponse<List<FieldError>> {
            return ApiResponse(
                code = ErrorCode.BAD_REQUEST.code,
                message = ErrorCode.BAD_REQUEST.message,
                content = errors,
            )
        }
    }
}

data class FieldError(
    val field: String,
    val message: String,
)
