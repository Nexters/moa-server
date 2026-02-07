package com.moa.common.auth

import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class OnboardingAuthMemberResolver(
    private val jwtTokenProvider: JwtTokenProvider,
    private val request: HttpServletRequest,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(OnboardingAuth::class.java) &&
                parameter.parameterType == AuthenticatedMemberInfo::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthenticatedMemberInfo {
        val token = jwtTokenProvider.extractToken(request)
            ?: throw UnauthorizedException()

        try {
            jwtTokenProvider.validateToken(token)

            val memberId = jwtTokenProvider.getUserIdFromToken(token)
                ?: throw UnauthorizedException()

            return AuthenticatedMemberInfo(id = memberId)
        } catch (ex: ExpiredJwtException) {
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        } catch (ex: Exception) {
            throw UnauthorizedException()
        }
    }
}
