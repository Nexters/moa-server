package com.moa.common.exception

class ForbiddenException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
