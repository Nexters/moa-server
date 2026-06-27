package com.moa.service

import com.moa.common.auth.JwtTokenProvider
import com.moa.common.oidc.OidcIdTokenValidator
import com.moa.common.oidc.OidcUserInfo
import com.moa.entity.Member
import com.moa.entity.ProviderType
import com.moa.repository.MemberRepository
import com.moa.service.dto.KaKaoSignInUpRequest
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthServiceRefreshTest {

    private val oidcIdTokenValidator = mockk<OidcIdTokenValidator>()
    private val jwtTokenProvider = mockk<JwtTokenProvider>(relaxed = true)
    private val memberRepository = mockk<MemberRepository>()
    private val fcmTokenService = mockk<FcmTokenService>(relaxed = true)
    private val refreshTokenService = mockk<RefreshTokenService>(relaxed = true)

    private val sut = AuthService(
        oidcIdTokenValidator,
        jwtTokenProvider,
        memberRepository,
        fcmTokenService,
        refreshTokenService,
    )

    @Test
    fun `기존 회원 로그인 응답에 발급된 refreshToken 이 포함된다`() {
        every { oidcIdTokenValidator.validate(any(), any()) } returns OidcUserInfo("sub", ProviderType.KAKAO)
        every { memberRepository.findByProviderAndProviderSubject(any(), any()) } returns
                Member(id = 7L, provider = ProviderType.KAKAO, providerSubject = "sub")
        every { jwtTokenProvider.createAccessToken(7L) } returns "access-7"
        every { refreshTokenService.issue(7L, any()) } returns "refresh-7"

        val response = sut.kakaoSignInUp(KaKaoSignInUpRequest(idToken = "tok"))

        assertThat(response.accessToken).isEqualTo("access-7")
        assertThat(response.refreshToken).isEqualTo("refresh-7")
    }

    @Test
    fun `신규 가입 응답에도 refreshToken 이 포함된다`() {
        every { oidcIdTokenValidator.validate(any(), any()) } returns OidcUserInfo("sub-new", ProviderType.KAKAO)
        every { memberRepository.findByProviderAndProviderSubject(any(), any()) } returns null
        every { memberRepository.save(any<Member>()) } returns Member(id = 9L, provider = ProviderType.KAKAO, providerSubject = "sub-new")
        every { jwtTokenProvider.createAccessToken(9L) } returns "access-9"
        every { refreshTokenService.issue(9L, any()) } returns "refresh-9"

        val response = sut.kakaoSignInUp(KaKaoSignInUpRequest(idToken = "tok"))

        assertThat(response.refreshToken).isEqualTo("refresh-9")
    }

    @Test
    fun `refresh 는 회전 결과로 새 access 와 새 refresh 를 반환한다`() {
        every { refreshTokenService.rotate("old-refresh", any()) } returns
                RefreshTokenService.RotationResult(memberId = 7L, plainRefreshToken = "new-refresh")
        every { jwtTokenProvider.createAccessToken(7L) } returns "new-access"

        val response = sut.refresh(com.moa.service.dto.TokenRefreshRequest("old-refresh"))

        assertThat(response.accessToken).isEqualTo("new-access")
        assertThat(response.refreshToken).isEqualTo("new-refresh")
    }

    @Test
    fun `logout 은 refreshToken 이 있으면 체인을 무효화한다`() {
        sut.logout(7L, com.moa.service.dto.LogoutRequest(fcmDeviceToken = "fcm", refreshToken = "r"))

        io.mockk.verify { refreshTokenService.revokeByPlainToken("r", any()) }
    }

    @Test
    fun `logout 은 refreshToken 이 없으면 무효화를 호출하지 않는다`() {
        sut.logout(7L, com.moa.service.dto.LogoutRequest(fcmDeviceToken = "fcm", refreshToken = null))

        io.mockk.verify(exactly = 0) { refreshTokenService.revokeByPlainToken(any(), any()) }
    }
}
