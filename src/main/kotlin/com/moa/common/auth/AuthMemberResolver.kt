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
                parameter.parameterType == AuthMemberInfo::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): AuthMemberInfo {
        val memberId = resolveMemberId()
        validateOnboardingCompleted(memberId)
        return AuthMemberInfo(id = memberId)
    }

    private fun resolveMemberId(): Long {
        val token = jwtTokenProvider.extractToken(request)
            ?: throw UnauthorizedException()

        return try {
            jwtTokenProvider.validateToken(token)
            jwtTokenProvider.getUserIdFromToken(token)
        } catch (ex: ExpiredJwtException) {
            throw UnauthorizedException(ErrorCode.EXPIRED_TOKEN)
        } catch (ex: Exception) {
            throw UnauthorizedException()
        } ?: throw UnauthorizedException()
    }

    private fun validateOnboardingCompleted(memberId: Long,  today: LocalDate = LocalDate.now()) {
        val profileCompleted = isProfileCompleted(memberId)
        val payrollCompleted = isPayrollCompleted(memberId, today)
        val workPolicyCompleted = isWorkPolicyCompleted(memberId, today)
        val hasRequiredTermsAgreed = hasRequiredTermsAgreed(memberId)

        val onboardingCompleted =
            profileCompleted && payrollCompleted && workPolicyCompleted && hasRequiredTermsAgreed

        if (!onboardingCompleted) {
            throw ForbiddenException(ErrorCode.ONBOARDING_INCOMPLETE)
        }
    }

    private fun isProfileCompleted(memberId: Long): Boolean {
        return profileRepository.findByMemberId(memberId)
            ?.let { it.nickname.isNotBlank() }
            ?: false
    }

    private fun isPayrollCompleted(memberId: Long, today: LocalDate): Boolean {
        return payrollVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today) != null
    }

    private fun isWorkPolicyCompleted(memberId: Long, today: LocalDate): Boolean {
        return workPolicyVersionRepository
            .findTopByMemberIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(memberId, today)
            ?.workdays
            ?.isNotEmpty()
            ?: false
    }

    private fun hasRequiredTermsAgreed(memberId: Long): Boolean {
        val requiredCodes = termRepository.findAll()
            .asSequence()
            .filter { it.required }
            .map { it.code }
            .toSet()

        val agreements = termAgreementRepository.findAllByMemberId(memberId)
            .associate { it.termCode to it.agreed }

        return requiredCodes.all { agreements[it] == true }
    }
}
