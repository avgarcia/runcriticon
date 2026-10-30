package com.runcriticon.planificacion.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.testing.IntegrationTestBase
import com.runcriticon.testing.OutboxDeliveries
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

/**
 * Derecho de supresión en `planificacion` de extremo a extremo: `AlumnoEliminado` y `EntrenadorEliminado` salen por
 * el outbox y [PlanificacionDeletionListener] borra lo que este módulo guarda de la persona. Siembra las filas por
 * JDBC, como `SeguimientoDeletionEventFlowIntegrationTest`, en todas las tablas con una persona de este esquema; las
 * que la guía [PlanificacionPersonColumnsErasureGuardIntegrationTest] enumera.
 */
class PlanificacionDeletionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    private val club = UUID.randomUUID()
    private val group = UUID.randomUUID()

    @Test
    fun `un alumno eliminado pierde pertenencia, snapshot y personalizaciones, y las de otro alumno siguen`() {
        val coach = UUID.randomUUID()
        val student = UUID.randomUUID()
        val otherStudent = UUID.randomUUID()
        val plan = seedPlan(coach)
        val session = seedSession(plan)
        for (person in listOf(student, otherStudent)) {
            seedStudentMembership(person)
            seedSnapshot(plan, person)
            seedPersonalization(plan, session, person)
        }
        val event = studentDeleted(student)

        publishAndAwait(event)

        STUDENT_TABLES.forEach { (table, column) ->
            countRows(table, column, student) shouldBe 0
            countRows(table, column, otherStudent) shouldBe 1
        }
        // El plan y su sesión son del entrenador, no del alumno: sobreviven.
        countRows("plan_semanal", "id", plan) shouldBe 1
        countRows("sesion", "id", session) shouldBe 1
    }

    @Test
    fun `un entrenador eliminado pierde sus planes con todo lo que cuelga de ellos y sus filas de grupo`() {
        val coach = UUID.randomUUID()
        val otherCoach = UUID.randomUUID()
        val student = UUID.randomUUID()
        val coachPlan = seedPlan(coach)
        val coachSession = seedSession(coachPlan)
        val otherPlan = seedPlan(otherCoach)
        val otherSession = seedSession(otherPlan)
        for ((plan, session) in listOf(coachPlan to coachSession, otherPlan to otherSession)) {
            seedSnapshot(plan, student)
            seedPersonalization(plan, session, student)
        }
        seedCoachMembership(coach)
        seedCoachMembership(otherCoach)
        val event = coachDeleted(coach)

        publishAndAwait(event)

        countRows("plan_semanal", "entrenador_id", coach) shouldBe 0
        for (table in listOf("sesion", "personalizacion", "plan_snapshot_alumno")) {
            countRows(table, "plan_id", coachPlan) shouldBe 0
            countRows(table, "plan_id", otherPlan) shouldBe 1
        }
        COACH_TABLES.forEach { (table, column) ->
            countRows(table, column, coach) shouldBe 0
            countRows(table, column, otherCoach) shouldBe 1
        }
    }

    @Test
    fun `reentregar la misma baja no la procesa dos veces ni duplica el registro de evento procesado`() {
        val student = UUID.randomUUID()
        seedStudentMembership(student)
        val event = studentDeleted(student)

        publishAndAwait(event)
        countRows("miembro_grupo", "persona_id", student) shouldBe 0

        // Una fila nueva de la misma persona: si la reentrega volviera a ejecutar el borrado, desaparecería.
        seedStudentMembership(student)
        transactions.executeWithoutResult { events.publishEvent(event) }
        OutboxDeliveries(jdbc).awaitCompleted(PlanificacionDeletionListener::class, event.eventId, times = 2)

        countRows("miembro_grupo", "persona_id", student) shouldBe 1
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.evento_procesado WHERE listener = ? AND event_id = ?",
            Int::class.java,
            "PlanificacionDeletionListener",
            event.eventId,
        ) shouldBe 1
    }

    private fun publishAndAwait(event: AlumnoEliminado) {
        transactions.executeWithoutResult { events.publishEvent(event) }
        OutboxDeliveries(jdbc).awaitCompleted(PlanificacionDeletionListener::class, event.eventId)
    }

    private fun publishAndAwait(event: EntrenadorEliminado) {
        transactions.executeWithoutResult { events.publishEvent(event) }
        OutboxDeliveries(jdbc).awaitCompleted(PlanificacionDeletionListener::class, event.eventId)
    }

    private fun studentDeleted(student: UUID) =
        AlumnoEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = student,
            occurredAt = Instant.now(),
            clubId = club,
            actorId = null,
            traceparent = null,
        )

    private fun coachDeleted(coach: UUID) =
        EntrenadorEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = coach,
            occurredAt = Instant.now(),
            clubId = club,
            actorId = null,
            traceparent = null,
        )

    private fun seedPlan(coach: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO planificacion.plan_semanal (id, club_id, grupo_id, entrenador_id, semana, estado)
            VALUES (?, ?, ?, ?, '2026-08-17', 'PUBLICADO')
            """.trimIndent(),
            id,
            club,
            group,
            coach,
        )
        return id
    }

    private fun seedSession(plan: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO planificacion.sesion (id, plan_id, dia, tipo) VALUES (?, ?, '2026-08-17', 'RODAJE')",
            id,
            plan,
        )
        return id
    }

    private fun seedSnapshot(
        plan: UUID,
        student: UUID,
    ) {
        jdbc.update(
            "INSERT INTO planificacion.plan_snapshot_alumno (plan_id, club_id, alumno_id) VALUES (?, ?, ?)",
            plan,
            club,
            student,
        )
    }

    private fun seedPersonalization(
        plan: UUID,
        session: UUID,
        student: UUID,
    ) {
        jdbc.update(
            "INSERT INTO planificacion.personalizacion (id, plan_id, sesion_id, alumno_id) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(),
            plan,
            session,
            student,
        )
    }

    private fun seedStudentMembership(student: UUID) = seedMembership(student, "ALUMNO")

    /** La fila ENTRENADOR y su marca de agua, como las deja `GroupMembersProjectionJdbc` tras una asignación. */
    private fun seedCoachMembership(coach: UUID) {
        seedMembership(coach, "ENTRENADOR")
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo_entrenador_version
                (grupo_id, persona_id, club_id, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, now())
            """.trimIndent(),
            group,
            coach,
            club,
            UUID.randomUUID(),
        )
    }

    private fun seedMembership(
        person: UUID,
        role: String,
    ) {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, now())
            """.trimIndent(),
            group,
            club,
            person,
            role,
            UUID.randomUUID(),
        )
    }

    private fun countRows(
        table: String,
        column: String,
        value: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.$table WHERE $column = ?",
            Int::class.java,
            value,
        ) ?: 0

    private companion object {
        /** Tablas con una columna del alumno que el borrado del alumno vacía. */
        val STUDENT_TABLES =
            listOf(
                "miembro_grupo" to "persona_id",
                "plan_snapshot_alumno" to "alumno_id",
                "personalizacion" to "alumno_id",
            )

        /** Tablas con una columna del entrenador que el borrado del entrenador vacía. */
        val COACH_TABLES =
            listOf(
                "miembro_grupo" to "persona_id",
                "miembro_grupo_entrenador_version" to "persona_id",
            )
    }
}
