package com.moa.common.exception

class UnauthorizedException(
    val errorCode: ErrorCode = ErrorCode.UNAUTHORIZED,
) : RuntimeException(errorCode.message)
