package com.runcriticon.identidad.infrastructure.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("runcriticon.email")
data class EmailConfig(
    val fromAddress: String,
    val fromName: String,
    val baseUrl: String,
    val postmark: Postmark,
) {
    data class Postmark(
        val apiKey: String,
        val serverUrl: String,
    )
}

@Configuration
@EnableConfigurationProperties(EmailConfig::class)
internal class EmailConfigRegistration
