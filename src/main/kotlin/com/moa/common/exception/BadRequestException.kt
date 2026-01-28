package com.moa.common.exception

class BadRequestException(
    val errorCode: ErrorCode = ErrorCode.BAD_REQUEST,
) : RuntimeException(errorCode.message)
