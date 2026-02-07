package com.moa.service

import com.moa.common.auth.JwtTokenProvider
import com.moa.common.oidc.OidcIdTokenValidator
import com.moa.common.oidc.apple.AppleOAuthClient
import com.moa.entity.Member
import com.moa.entity.ProviderType
import com.moa.repository.MemberRepository
import com.moa.service.dto.AppleSignInUpRequest
import com.moa.service.dto.AppleSignInUpResponse
import com.moa.service.dto.KaKaoSignInUpRequest
import com.moa.service.dto.KakaoSignInUpResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val oidcIdTokenValidator: OidcIdTokenValidator,
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberRepository: MemberRepository,
    private val appleOAuthClient: AppleOAuthClient,
) {

    @Transactional
    fun kakaoSignInUp(request: KaKaoSignInUpRequest): KakaoSignInUpResponse {
        val userInfo = oidcIdTokenValidator.validate(ProviderType.KAKAO, request.idToken)

        val member = memberRepository.findByProviderAndProviderSubject(
            provider = userInfo.provider,
            providerSubject = userInfo.subject,
        )

        member?.let {
            return KakaoSignInUpResponse(
                jwtTokenProvider.createAccessToken(member.id)
            )
        }

        val registeredMember = memberRepository.save(
            Member(
                provider = ProviderType.KAKAO,
                providerSubject = userInfo.subject,
                profile = null,
            )
        )

        val registerToken = jwtTokenProvider.createAccessToken(
            registeredMember.id
        )

        return KakaoSignInUpResponse(
            registerToken,
        )
    }

    @Transactional
    fun appleSignInUp(request: AppleSignInUpRequest): AppleSignInUpResponse {
        val idToken = appleOAuthClient.exchangeCodeForIdToken(request.code)
        val userInfo = oidcIdTokenValidator.validate(ProviderType.APPLE, idToken)

        val member = memberRepository.findByProviderAndProviderSubject(
            provider = userInfo.provider,
            providerSubject = userInfo.subject,
        )

        member?.let {
            return AppleSignInUpResponse(
                jwtTokenProvider.createAccessToken(member.id)
            )
        }

        val registeredMember = memberRepository.save(
            Member(
                provider = ProviderType.APPLE,
                providerSubject = userInfo.subject,
                profile = null,
            )
        )

        val registerToken = jwtTokenProvider.createAccessToken(
            registeredMember.id
        )

        return AppleSignInUpResponse(
            registerToken,
        )
    }
}
