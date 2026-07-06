package com.moa.controller

import com.moa.service.AppleDesktopAuthService
import com.moa.service.AuthService
import com.moa.service.dto.TokenRefreshRequest
import com.moa.service.dto.TokenRefreshResponse
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthControllerTest {

    private val authService = mockk<AuthService>()
    private val appleDesktopAuthService = mockk<AppleDesktopAuthService>()
    private val sut = AuthController(authService, appleDesktopAuthService)

    @Test
    fun `refresh 는 서비스 결과를 ApiResponse 로 감싸 반환한다`() {
        every { authService.refresh(any()) } returns TokenRefreshResponse("new-access", "new-refresh")

        val response = sut.refresh(TokenRefreshRequest("old-refresh"))

        val body = response.body!!.content
        assertThat(body?.accessToken).isEqualTo("new-access")
        assertThat(body?.refreshToken).isEqualTo("new-refresh")
    }
}
