package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Vigila que ninguna columna de persona del esquema `seguimiento` quede fuera del borrado. `seguimiento` es JDBC puro,
 * así que `RgpdArchTest`, que engancha en `@Entity`, no la ve; y los tests de flujo de
 * [SeguimientoDeletionEventFlowIntegrationTest] solo prueban las tablas que ya conocen. Sin este guarda, una tabla
 * nueva con un alumno o un entrenador no rompería nada: `SeguimientoErasureJdbc` seguiría borrando lo de antes y el
 * derecho de supresión se degradaría en silencio. Aquí el residuo es además dato de salud — reportes de sesión,
 * marcas y reajustes por molestias o lesión son categoría 1 (ADR-0014 D5).
 *
 * Enumera las columnas desde `information_schema` y las compara con las que el borrado cubre. Si falla, hay una
 * columna de persona nueva: añádela a `SeguimientoErasureJdbc`, siémbrala en el test de flujo y escríbela aquí.
 */
class SeguimientoPersonColumnsErasureGuardIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `toda columna de persona del esquema seguimiento esta cubierta por el borrado`() {
        val found =
            jdbc
                .queryForList(
                    """
                    SELECT table_name || '.' || column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'seguimiento'
                      AND (column_name ~ '(alumno|persona|entrenador|usuario|autor|creador)_id$'
                           OR column_name IN ('email', 'nombre', 'name', 'telefono', 'phone'))
                    ORDER BY 1
                    """.trimIndent(),
                    String::class.java,
                ).toSet()

        withClue(
            "El esquema seguimiento tiene columnas de persona que el borrado no conoce (o el borrado cubre una " +
                "que ya no existe). Revisa SeguimientoErasureJdbc y actualiza COVERED:",
        ) {
            found shouldBe COVERED.keys
        }
    }

    private companion object {
        /** Columna de persona → qué `DELETE` la vacía (`SeguimientoErasureJdbc` salvo que se indique otro). */
        val COVERED =
            mapOf(
                "plan_resuelto_por_alumno.alumno_id" to "DELETE_BY_STUDENT_SQL",
                "reporte_sesion.alumno_id" to "DELETE_REPORTS_BY_STUDENT_SQL",
                "reajuste_dia.alumno_id" to "DELETE_ADJUSTMENTS_BY_STUDENT_SQL",
                "marca_alumno.alumno_id" to "DELETE_MARKS_BY_STUDENT_SQL",
                "consentimiento_alumno.alumno_id" to "DELETE_SQL de ConsentProjectionJdbc",
                "grupo_entrenador.entrenador_id" to "DELETE_COACH_GROUPS_BY_COACH_SQL",
                "grupo_entrenador_version.entrenador_id" to "DELETE_COACH_WATERMARKS_BY_COACH_SQL",
            )
    }
}
