package com.moa.common.auth

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedMember

data class AuthenticatedMemberInfo(
    val id: Long,
)
