package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.planificacion.application.ports.outbound.persistence.PlanificacionErasure
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Borrado físico de `plan_snapshot_alumno` (LAL-25) contra Postgres real: un alumno con derecho al olvido no
 * debe seguir apareciendo en el snapshot congelado de ningún plan, publicado o no, ni cuando el plan es del
 * entrenador borrado (borrado en cascada de la raíz del agregado).
 */
class PlanificacionErasureJdbcIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var erasure: PlanificacionErasure

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = UuidCreator.getTimeOrderedEpoch()

    @Test
    fun `borrar un alumno quita sus entradas de snapshot en cualquier plan`() {
        val alumno = UUID.randomUUID()
        val otroAlumno = UUID.randomUUID()
        val plan = sembrarPlanPublicado(entrenador = UUID.randomUUID())
        sembrarSnapshot(plan, alumno)
        sembrarSnapshot(plan, otroAlumno)

        val result = erasure.erase(PersonId.of(alumno))

        result.snapshotEntries shouldBe 1
        contarSnapshot(plan, alumno) shouldBe 0
        contarSnapshot(plan, otroAlumno) shouldBe 1
    }

    @Test
    fun `borrar al entrenador arrastra el snapshot de todos sus planes`() {
        val entrenador = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val plan = sembrarPlanPublicado(entrenador)
        sembrarSnapshot(plan, alumno)

        val result = erasure.erase(PersonId.of(entrenador))

        result.plans shouldBe 1
        contarSnapshot(plan, alumno) shouldBe 0
    }

    @Test
    fun `repetir el borrado sobre alguien ya borrado no falla`() {
        val alumno = UUID.randomUUID()

        erasure.erase(PersonId.of(alumno))
        val second = erasure.erase(PersonId.of(alumno))

        second.snapshotEntries shouldBe 0
    }

    private fun sembrarPlanPublicado(entrenador: UUID): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO planificacion.plan_semanal (id, club_id, grupo_id, entrenador_id, semana, estado)
            VALUES (?, ?, ?, ?, '2026-08-17', 'PUBLICADO')
            """.trimIndent(),
            id,
            club,
            UuidCreator.getTimeOrderedEpoch(),
            entrenador,
        )
        return id
    }

    private fun sembrarSnapshot(
        planId: UUID,
        alumnoId: UUID,
    ) {
        jdbc.update(
            "INSERT INTO planificacion.plan_snapshot_alumno (plan_id, club_id, alumno_id) VALUES (?, ?, ?)",
            planId,
            club,
            alumnoId,
        )
    }

    private fun contarSnapshot(
        planId: UUID,
        alumnoId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.plan_snapshot_alumno WHERE plan_id = ? AND alumno_id = ?",
            Int::class.java,
            planId,
            alumnoId,
        ) ?: 0
}
