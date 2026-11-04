package com.runcriticon.auditoria.application.listeners

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Vigila que ninguna columna de persona del esquema `auditoria` quede fuera de la anonimización. Los asientos de
 * autorización no se borran al suprimir a una persona: [AuditTrailAnonymizationListener] pone a `NULL` las columnas
 * donde aparece (ADR-0014 D6, categoría 3), y [AuditTrailAnonymizationIntegrationTest] solo prueba las que ya conoce.
 * Sin este guarda, una columna nueva con un actor o un sujeto sobreviviría a la supresión sin que nada lo notara.
 *
 * Enumera las columnas desde `information_schema` y las compara con las que la anonimización cubre. Si falla, hay una
 * columna de persona nueva: añádela al `ANONYMIZE_SQL` de `AuditEventRepositoryImpl`, siémbrala en
 * [AuditTrailAnonymizationIntegrationTest] y escríbela aquí. Las columnas de texto libre (`recurso`, `motivo`) quedan
 * fuera del patrón: hoy solo guardan el recurso y la acción de la matriz y un código fijo de denegación.
 */
class AuditoriaPersonColumnsErasureGuardIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `toda columna de persona del esquema auditoria esta cubierta por la anonimizacion`() {
        val found =
            jdbc
                .queryForList(
                    """
                    SELECT table_name || '.' || column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'auditoria'
                      AND (column_name ~ '(alumno|persona|entrenador|usuario|autor|creador|actor|sujeto)_id$'
                           OR column_name IN ('email', 'nombre', 'name', 'telefono', 'phone', 'ip', 'user_agent'))
                    ORDER BY 1
                    """.trimIndent(),
                    String::class.java,
                ).toSet()

        withClue(
            "El esquema auditoria tiene columnas de persona que la anonimización no conoce (o la anonimización " +
                "cubre una que ya no existe). Revisa el ANONYMIZE_SQL de AuditEventRepositoryImpl y actualiza " +
                "COVERED:",
        ) {
            found shouldBe COVERED.keys
        }
    }

    private companion object {
        /** Columna de persona → qué la vacía. */
        val COVERED =
            mapOf(
                "evento.actor_id" to "anonimizada a NULL por ANONYMIZE_SQL (AuditTrailAnonymizationListener)",
                "evento.sujeto_id" to "anonimizada a NULL por ANONYMIZE_SQL (AuditTrailAnonymizationListener)",
            )
    }
}
