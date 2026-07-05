package com.moa.service

import com.moa.common.auth.JwtTokenProvider
import com.moa.common.exception.UnauthorizedException
import com.moa.common.oidc.OidcIdTokenValidator
import com.moa.entity.Member
import com.moa.entity.ProviderType
import com.moa.repository.MemberRepository
import com.moa.service.dto.AppleSignInUpRequest
import com.moa.service.dto.KaKaoSignInUpRequest
import com.moa.service.dto.LogoutRequest
import com.moa.service.dto.SignInUpResponse
import com.moa.service.dto.TokenRefreshRequest
import com.moa.service.dto.TokenRefreshResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val oidcIdTokenValidator: OidcIdTokenValidator,
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberRepository: MemberRepository,
    private val fcmTokenService: FcmTokenService,
    private val refreshTokenService: RefreshTokenService,
) {

    @Transactional
    fun kakaoSignInUp(request: KaKaoSignInUpRequest): SignInUpResponse {
        val userInfo = oidcIdTokenValidator.validate(ProviderType.KAKAO, request.idToken)
        return signInUp(ProviderType.KAKAO, userInfo.subject, request.fcmDeviceToken)
    }

    @Transactional
    fun appleSignInUp(request: AppleSignInUpRequest): SignInUpResponse {
        val userInfo = oidcIdTokenValidator.validate(ProviderType.APPLE, request.idToken)
        return signInUp(ProviderType.APPLE, userInfo.subject, request.fcmDeviceToken)
    }

    /** subject 는 검증이 끝난 값이어야 한다. 회원 확정과 토큰 발급을 한 트랜잭션으로 묶는다. */
    @Transactional
    fun signInUp(provider: ProviderType, subject: String, fcmDeviceToken: String? = null): SignInUpResponse {
        val memberId = resolveMemberId(provider, subject)
        fcmDeviceToken?.let { fcmTokenService.registerToken(memberId, it) }
        return issueTokens(memberId)
    }

    private fun resolveMemberId(provider: ProviderType, subject: String): Long {
        memberRepository.findByProviderAndProviderSubject(provider, subject)?.let { return it.id }
        return memberRepository.save(Member(provider = provider, providerSubject = subject)).id
    }

    private fun issueTokens(memberId: Long): SignInUpResponse = SignInUpResponse(
        userId = memberId,
        accessToken = jwtTokenProvider.createAccessToken(memberId),
        refreshToken = refreshTokenService.issue(memberId),
    )

    @Transactional(noRollbackFor = [UnauthorizedException::class])
    fun refresh(request: TokenRefreshRequest): TokenRefreshResponse {
        val rotation = refreshTokenService.rotate(request.refreshToken)
        return TokenRefreshResponse(
            accessToken = jwtTokenProvider.createAccessToken(rotation.memberId),
            refreshToken = rotation.plainRefreshToken,
        )
    }

    @Transactional
    fun logout(memberId: Long, request: LogoutRequest) {
        fcmTokenService.deleteToken(memberId, request.fcmDeviceToken)
        request.refreshToken?.let { refreshTokenService.revokeByPlainToken(it) }
    }
}
