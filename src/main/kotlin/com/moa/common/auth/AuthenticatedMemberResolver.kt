package com.moa.common.auth

import com.moa.common.exception.BadRequestException
import com.moa.common.exception.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class AuthenticatedMemberResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(AuthenticatedMember::class.java) &&
                parameter.parameterType == AuthenticatedMemberInfo::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedMemberInfo {
        val memberId = webRequest.getAttribute(
            AuthConstants.CURRENT_MEMBER_ID,
            RequestAttributes.SCOPE_REQUEST
        ) as? Long ?: throw BadRequestException(ErrorCode.INVALID_ID_TOKEN)

        return AuthenticatedMemberInfo(id = memberId)
    }
}
