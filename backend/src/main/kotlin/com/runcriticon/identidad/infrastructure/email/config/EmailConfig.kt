package com.runcriticon.identidad.infrastructure.email.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuración del envío de emails, mapeada desde el prefijo `runcriticon.email`.Los valores se definen por entorno en
 * `application.yml`; el secreto `postmark.apiKey` llega como variable de entorno desde SSM `SecureString`, nunca
 * embebido en el código.
 *
 * @property fromAddress dirección remitente del email transaccional.
 * @property fromName nombre mostrado del remitente.
 * @property baseUrl URL base del frontend, usada para construir el enlace de activación.
 * @property postmark credenciales y endpoint del proveedor Postmark.
 */
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

/**
 * Registra [EmailConfig] como bean de propiedades. Aislada en su propia clase para no obligar a anotar la `data class`
 * con `@Component`/`@ConfigurationPropertiesScan`.
 */
@Configuration
@EnableConfigurationProperties(EmailConfig::class)
internal class EmailConfigRegistration
