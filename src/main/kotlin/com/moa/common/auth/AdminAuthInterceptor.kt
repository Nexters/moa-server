package com.moa.common.auth

import com.moa.common.exception.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AdminAuthInterceptor(
    private val adminProperties: AdminProperties,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler is HandlerMethod && handler.hasMethodAnnotation(AdminAuth::class.java)) {
            val key = request.getHeader(ADMIN_KEY_HEADER)
            if (key != adminProperties.apiKey) {
                throw UnauthorizedException()
            }
        }
        return true
    }

    companion object {
        private const val ADMIN_KEY_HEADER = "X-Admin-Key"
    }
}
