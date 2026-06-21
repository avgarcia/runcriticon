package com.runcriticon.identidad.infrastructure.email

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import java.time.Instant

class InvitationEmailRendererTest {
    private val renderer = InvitationEmailRenderer(buildTemplateEngine())

    @Test
    fun `render incluye el nombre y el enlace de activacion`() {
        val html =
            renderer.render(
                recipientName = "Carlos",
                activationUrl = "https://app.runcriticon.com/activar?token=abc",
                expiresAt = Instant.parse("2026-06-26T10:00:00Z"),
            )

        html shouldContain "Carlos"
        html shouldContain "https://app.runcriticon.com/activar?token=abc"
    }

    @Test
    fun `render escapa el HTML del nombre para evitar inyeccion`() {
        val html =
            renderer.render(
                recipientName = "<script>alert(1)</script>",
                activationUrl = "https://app.runcriticon.com/activar?token=abc",
                expiresAt = Instant.parse("2026-06-26T10:00:00Z"),
            )

        html shouldNotContain "<script>alert(1)</script>"
        html shouldContain "&lt;script&gt;"
    }

    private fun buildTemplateEngine(): TemplateEngine {
        val resolver =
            ClassLoaderTemplateResolver().apply {
                prefix = "templates/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
            }
        return TemplateEngine().apply { setTemplateResolver(resolver) }
    }
}
