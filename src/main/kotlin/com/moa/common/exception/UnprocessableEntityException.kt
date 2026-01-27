package com.moa.common.exception

class UnprocessableEntityException(
    val errorCode: ErrorCode = ErrorCode.UNPROCESSABLE_ENTITY,
) : RuntimeException(errorCode.message)
