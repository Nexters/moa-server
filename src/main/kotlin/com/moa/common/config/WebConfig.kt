package com.moa.common.config

import com.moa.common.auth.AuthMemberResolver
import com.moa.common.auth.OnboardingAuthMemberResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val onboardingAuthMemberResolver: OnboardingAuthMemberResolver,
    private val authMemberResolver: AuthMemberResolver,
) : WebMvcConfigurer {

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(onboardingAuthMemberResolver)
        resolvers.add(authMemberResolver)
    }
}
