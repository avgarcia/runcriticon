package com.runcriticon.planificacion.application.listeners

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Vigila que ninguna columna de persona del esquema `planificacion` quede fuera del borrado. `planificacion` es JDBC
 * puro, así que `RgpdArchTest`, que engancha en `@Entity`, no la ve; y los tests de flujo de
 * [PlanificacionDeletionEventFlowIntegrationTest] solo prueban las tablas que ya conocen. Sin este guarda, una tabla
 * nueva con un alumno o un entrenador no rompería nada: `PlanificacionErasureJdbc` seguiría borrando lo de antes y el
 * derecho de supresión se degradaría en silencio, sin tablas de auditoría propias con las que reconstruir el residuo.
 *
 * Enumera las columnas desde `information_schema` y las compara con las que el borrado cubre. Si falla, hay una
 * columna de persona nueva: añádela a `PlanificacionErasureJdbc`, siémbrala en el test de flujo y escríbela aquí.
 */
class PlanificacionPersonColumnsErasureGuardIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `toda columna de persona del esquema planificacion esta cubierta por el borrado`() {
        val found =
            jdbc
                .queryForList(
                    """
                    SELECT table_name || '.' || column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'planificacion'
                      AND (column_name ~ '(alumno|persona|entrenador|usuario|autor|creador)_id$'
                           OR column_name IN ('email', 'nombre', 'name', 'telefono', 'phone'))
                    ORDER BY 1
                    """.trimIndent(),
                    String::class.java,
                ).toSet()

        withClue(
            "El esquema planificacion tiene columnas de persona que el borrado no conoce (o el borrado cubre una " +
                "que ya no existe). Revisa PlanificacionErasureJdbc y actualiza COVERED:",
        ) {
            found shouldBe COVERED.keys
        }
    }

    private companion object {
        /** Columna de persona → qué `DELETE` de `PlanificacionErasureJdbc` la vacía. */
        val COVERED =
            mapOf(
                "personalizacion.alumno_id" to "DELETE_PERSONALIZATION_BY_STUDENT_SQL",
                "plan_snapshot_alumno.alumno_id" to "DELETE_SNAPSHOT_BY_STUDENT_SQL",
                "miembro_grupo.persona_id" to "DELETE_GROUP_MEMBERSHIP_SQL",
                "miembro_grupo_entrenador_version.persona_id" to "DELETE_COACH_WATERMARK_SQL",
                "plan_semanal.entrenador_id" to "DELETE_PLAN_BY_COACH_SQL",
            )
    }
}
