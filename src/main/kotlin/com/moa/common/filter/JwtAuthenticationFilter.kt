package com.moa.common.filter

import com.moa.common.auth.AuthConstants
import com.moa.common.auth.JwtTokenProvider
import com.moa.common.exception.ErrorCode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    companion object {
        private val EXCLUDED_PATHS = listOf(
            "/api/v1/auth",
            "/h2-console",
        )
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return EXCLUDED_PATHS.any { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = jwtTokenProvider.extractToken(request)

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            writeUnauthorizedResponse(response)
            return
        }

        val memberId = jwtTokenProvider.getUserIdFromToken(token)
        if (memberId == null) {
            writeUnauthorizedResponse(response)
            return
        }

        request.setAttribute(AuthConstants.CURRENT_MEMBER_ID, memberId)
        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorizedResponse(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"

        val errorCode = ErrorCode.UNAUTHORIZED
        val errorResponse = mapOf(
            "code" to errorCode.code,
            "message" to errorCode.message,
            "content" to null
        )

        objectMapper.writeValue(response.writer, errorResponse)
    }
}
