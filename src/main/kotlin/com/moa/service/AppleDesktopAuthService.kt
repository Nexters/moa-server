package com.moa.service

import com.moa.common.auth.ExchangeCodeStore
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import com.moa.common.oidc.AppleTokenClient
import com.moa.common.oidc.OidcIdTokenValidator
import com.moa.entity.ProviderType
import com.moa.service.dto.SignInUpResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder

@Service
class AppleDesktopAuthService(
    private val appleTokenClient: AppleTokenClient,
    private val oidcIdTokenValidator: OidcIdTokenValidator,
    private val authService: AuthService,
    private val exchangeCodeStore: ExchangeCodeStore,
    @Value("\${oidc.apple.desktop-redirect-uri:http://127.0.0.1:17171/callback}")
    private val desktopRedirectUri: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Apple callback 처리 → localhost 리다이렉트 URL(성공/실패 모두 앱이 처리). */
    fun callback(code: String?, state: String?, appleError: String? = null): String {
        if (code.isNullOrBlank()) {
            log.warn("Apple desktop callback without code: error={}", appleError)
            return redirect("error", appleError ?: "login_failed", state)
        }
        return try {
            val idToken = appleTokenClient.exchangeCodeForIdToken(code)
            val userInfo = oidcIdTokenValidator.validate(ProviderType.APPLE, idToken)
            // 앱이 complete 를 호출할 때 회원 생성하도록 분리
            redirect("exchangeCode", exchangeCodeStore.issue(userInfo.subject), state)
        } catch (ex: Exception) {
            log.warn("Apple desktop callback failed", ex)
            redirect("error", "login_failed", state)
        }
    }

    fun complete(exchangeCode: String): SignInUpResponse {
        val subject = exchangeCodeStore.consume(exchangeCode)
            ?: throw UnauthorizedException(ErrorCode.INVALID_EXCHANGE_CODE)
        // signInUp 이 회원 확정 + 토큰 발급을 한 트랜잭션으로 묶는다 — 토큰 발급 실패 시 회원 생성도 롤백.
        return authService.signInUp(ProviderType.APPLE, subject)
    }

    private fun redirect(key: String, value: String, state: String?): String =
        UriComponentsBuilder.fromUriString(desktopRedirectUri)
            .queryParam(key, value)
            .apply { if (!state.isNullOrBlank()) queryParam("state", state) }
            .build()
            .encode()
            .toUriString()
}
