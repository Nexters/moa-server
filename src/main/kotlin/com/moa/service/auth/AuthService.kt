package com.moa.service.auth

import com.moa.common.auth.JwtTokenProvider
import com.moa.entity.Member
import com.moa.entity.TermAgreement
import com.moa.repository.MemberRepository
import com.moa.repository.TermAgreementRepository
import com.moa.repository.TermRepository
import com.moa.service.auth.oidc.OidcIdTokenValidator
import com.moa.service.auth.oidc.ProviderType
import com.moa.service.dto.KaKaoSignInUpRequest
import com.moa.service.dto.KakaoSignInUpResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val oidcIdTokenValidator: OidcIdTokenValidator,
    private val jwtTokenProvider: JwtTokenProvider,
    private val memberRepository: MemberRepository,
    private val termRepository: TermRepository,
    private val termAgreementRepository: TermAgreementRepository,
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

        createTermAgreementsForNewMember(registeredMember.id)

        val registerToken = jwtTokenProvider.createAccessToken(
            registeredMember.id
        )

        return KakaoSignInUpResponse(
            registerToken,
        )
    }

    private fun createTermAgreementsForNewMember(memberId: Long) {
        val activeTerms = termRepository.findAllByActiveTrue()

        val termAgreements = activeTerms.map { term ->
            TermAgreement(
                memberId = memberId,
                termCode = term.code,
                agreed = false,
            )
        }

        termAgreementRepository.saveAll(termAgreements)
    }
}
