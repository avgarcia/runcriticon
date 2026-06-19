package com.runcriticon.identidad.infrastructure.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties("runcriticon.email")
data class EmailConfig(
    val fromAddress: String = "",
    val fromName: String = "Runcriticon",
    val baseUrl: String = "http://localhost:4200",
    val smtpHost: String = "",
    val smtpPort: Int = 1025,
    val postmark: Postmark = Postmark(),
) {
    data class Postmark(
        val apiKey: String = "",
        val serverUrl: String = "https://api.postmarkapp.com",
    )
}

@Configuration
@EnableConfigurationProperties(EmailConfig::class)
internal class EmailConfigRegistration
