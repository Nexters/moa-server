package com.moa.common.auth

import com.moa.common.exception.ErrorCode
import com.moa.common.exception.ForbiddenException
import com.moa.common.exception.UnauthorizedException
import com.moa.repository.*
import io.jsonwebtoken.ExpiredJwtException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.LocalDate

@Component
class AuthMemberResolver(
    private val jwtTokenProvider: JwtTokenProvider,
    private val request: HttpServletRequest,
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
    private val profileRepository: ProfileRepository,
    private val payrollVersionRepository: PayrollVersionRepository,
    private val workPolicyVersionRepository: WorkPolicyVersionRepository,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(Auth::class.java) &&
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

        val memberId = try {
            jwtTokenProvider.validateToken(token)
            jwtTokenProvider.getUserIdFromToken(token)
        } catch (ex: ExpiredJwtException) {
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        } catch (ex: Exception) {
            throw UnauthorizedException()
        } ?: throw UnauthorizedException()

        val today = LocalDate.now()

        val profileCompleted = profileRepository.findByMemberId(memberId)
            ?.let { it.nickname.isNotBlank() && it.workplace.isNotBlank() }
            ?: false

        val payrollCompleted =
            payrollVersionRepository
                .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today) != null

        val workPolicyCompleted =
            workPolicyVersionRepository
                .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
                ?.workdays
                ?.isNotEmpty()
                ?: false

        val requiredCodes = termRepository.findAll()
            .asSequence()
            .filter { it.required }
            .map { it.code }
            .toSet()

        val agreements = termAgreementRepository.findAllByMemberId(memberId)
            .associate { it.termCode to it.agreed }

        val hasRequiredTermsAgreed = requiredCodes.all { agreements[it] == true }

        val onboardingCompleted =
            profileCompleted && payrollCompleted && workPolicyCompleted && hasRequiredTermsAgreed

        if (!onboardingCompleted) {
            throw ForbiddenException(ErrorCode.ONBOARDING_INCOMPLETE)
        }

        return AuthenticatedMemberInfo(id = memberId)
    }
}
