package com.moa.common.exception

class NotFoundException(
    val errorCode: ErrorCode = ErrorCode.RESOURCE_NOT_FOUND,
) : RuntimeException(errorCode.message)
