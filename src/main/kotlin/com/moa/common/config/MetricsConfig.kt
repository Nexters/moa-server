package com.moa.common.config

import io.micrometer.core.aop.TimedAspect
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
class MetricsConfig(
    @Value("\${spring.application.name}") private val appName: String,
    @Value("\${app.deploy-color}") private val deployColor: String,
    @Value("\${app.version}") private val appVersion: String,
) {
    @Bean
    fun commonTags() = MeterRegistryCustomizer<MeterRegistry> { registry ->
        registry.config().commonTags(
            "application", appName,
            "deploy_color", deployColor,
            "app_version", appVersion,
        )
    }

    @Bean
    fun timedAspect(registry: MeterRegistry) = TimedAspect(registry)
}
