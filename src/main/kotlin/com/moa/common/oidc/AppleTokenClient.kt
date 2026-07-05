package com.moa.common.oidc

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.moa.common.exception.ErrorCode
import com.moa.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

@Component
class AppleTokenClient(
    private val clientSecretGenerator: AppleClientSecretGenerator,
    config: OidcProviderConfig,
    // 사용자 요청 스레드에서 호출되므로 타임아웃 필수 — Apple 장애가 톰캣 스레드풀 고갈로 번지는 것을 막는다.
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.apple.tokenConnectTimeoutSeconds)).build(),
            ).apply { setReadTimeout(Duration.ofSeconds(config.apple.tokenReadTimeoutSeconds)) },
        )
        .build(),
) {
    private val apple = config.apple
    private val log = LoggerFactory.getLogger(javaClass)

    fun exchangeCodeForIdToken(code: String): String {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", apple.clientId)
            add("client_secret", clientSecretGenerator.generate())
            add("code", code)
            add("grant_type", "authorization_code")
            add("redirect_uri", apple.redirectUri)
        }
        val body = try {
            restClient.post()
                .uri(apple.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(AppleTokenResponse::class.java)
        } catch (ex: Exception) {
            // 원본 예외(네트워크/5xx/4xx/파싱)를 로그로 보존해 진단 가능하게 — 응답은 통일된 INVALID_ID_TOKEN.
            log.warn("Apple token exchange failed", ex)
            throw UnauthorizedException(ErrorCode.INVALID_ID_TOKEN)
        }
        return body?.idToken ?: run {
            log.warn("Apple token exchange returned no id_token: error={}", body?.error)
            throw UnauthorizedException(ErrorCode.INVALID_ID_TOKEN)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class AppleTokenResponse(
        @JsonProperty("id_token") val idToken: String? = null,
        val error: String? = null,
    )
}
