package com.moa.common.exception

class ForbiddenException(
    val errorCode: ErrorCode = ErrorCode.FORBIDDEN,
) : RuntimeException(errorCode.message)
