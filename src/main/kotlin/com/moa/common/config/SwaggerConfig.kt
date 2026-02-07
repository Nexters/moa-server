package com.moa.common.config

import com.moa.common.auth.OnboardingAuth
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    init {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(OnboardingAuth::class.java)
    }

    @Bean
    fun openAPI(): OpenAPI {
        val jwt = "JWT"
        val securitySchemeName = "bearerAuth"

        val securityRequirement = SecurityRequirement().addList(securitySchemeName)

        val securityScheme = SecurityScheme()
            .name(securitySchemeName)
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat(jwt)
            .`in`(SecurityScheme.In.HEADER)

        return OpenAPI()
            .components(Components().addSecuritySchemes(securitySchemeName, securityScheme))
            .addSecurityItem(securityRequirement)
            .servers(
                listOf(
                    Server().url("/")
                )
            )
            .info(apiInfo())
    }

    private fun apiInfo() = Info()
        .title("MOA SERVER API")
        .description("Nexters 28th MOA SERVER")
        .version("1.0.0")
}
