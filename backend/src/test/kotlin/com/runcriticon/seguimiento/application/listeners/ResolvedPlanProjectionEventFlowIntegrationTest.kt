package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Flujo completo de extremo a extremo (LAL-29): publicar `PlanPublicado` en la transacción de un caso de uso,
 * dejar que el outbox lo entregue tras el commit, y comprobar el estado final de `plan_resuelto_por_alumno`.
 * Mismo patrón que `PersonProjectionEventFlowIntegrationTest`/`GroupMembersProjectionEventFlowIntegrationTest`.
 */
class ResolvedPlanProjectionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `publicar un plan proyecta una fila por alumno y por sesion`() {
        val planId = UUID.randomUUID()
        val alumno1 = UUID.randomUUID()
        val alumno2 = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno1, alumno2),
                sessions =
                    listOf(
                        session("2026-08-17", "RODAJE"),
                        session("2026-08-19", "TEMPO", ritmoTipo = "ABSOLUTO", ritmoSegundosPorKm = 240),
                    ),
            ),
        )

        awaitRowCount(planId, expected = 4)
    }

    @Test
    fun `una sesion con ritmo absoluto persiste el segundos por km resuelto`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-19", "TEMPO", ritmoTipo = "ABSOLUTO", ritmoSegundosPorKm = 240)),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19"))
        row["ritmo_tipo_origen"] shouldBe "ABSOLUTO"
        row["ritmo_calculado_seg_por_km"] shouldBe 240
        row["ritmo_referencia_distancia"] shouldBe null
    }

    @Test
    fun `una sesion con ritmo relativo persiste falta de marca, no un segundos por km`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planId,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-19", "TEMPO", ritmoTipo = "RELATIVO", ritmoReferencia = "10K")),
            ),
        )

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19"))
        row["ritmo_calculado_seg_por_km"] shouldBe null
        row["ritmo_falta_marca"] shouldBe "10K"
    }

    @Test
    fun `las columnas de personalizacion se crean ya pero quedan sin rellenar`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()

        publish(planPublicado(planId = planId, students = listOf(alumno)))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-17"))
        row["mensaje_al_alumno"] shouldBe null
        row["es_personalizada"] shouldBe false
    }

    @Test
    fun `reentregar el mismo evento no duplica filas`() {
        val planId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val event = planPublicado(planId = planId, students = listOf(alumno))

        publish(event)
        awaitRowCount(planId, expected = 1)
        publish(event)

        Thread.sleep(SETTLE_MILLIS)
        countRows(planId) shouldBe 1
        countProcessed(event.eventId) shouldBe 1
    }

    @Test
    fun `dos planes de grupos distintos pueden resolver el mismo dia para el mismo alumno`() {
        val alumno = UUID.randomUUID()
        val planA = UUID.randomUUID()
        val planB = UUID.randomUUID()

        publish(
            planPublicado(
                planId = planA,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-17", "RODAJE")),
            ),
        )
        awaitRowCount(planA, expected = 1)
        publish(
            planPublicado(
                planId = planB,
                students = listOf(alumno),
                sessions = listOf(session("2026-08-17", "TEMPO")),
            ),
        )
        awaitRowCount(planB, expected = 1)

        // Sin UNIQUE (alumno_id, dia): las dos filas conviven, cada una con su plan_id — el desempate de cuál se
        // muestra es responsabilidad del lector (ResolvedPlanReaderIntegrationTest), no de este listener.
        countRowsForStudentAndDay(alumno, LocalDate.parse("2026-08-17")) shouldBe 2
    }

    /** Publica dentro de una transacción: `@ApplicationModuleListener` solo entrega tras un commit. */
    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRowCount(
        planId: UUID,
        expected: Int,
    ) {
        await("no se proyectaron las $expected filas del plan $planId") {
            countRows(planId).takeIf { it == expected }
        }
    }

    private fun awaitRow(
        planId: UUID,
        alumnoId: UUID,
        dia: LocalDate,
    ): Map<String, Any?> = await("no se proyectó la fila de $alumnoId/$dia") { readRow(planId, alumnoId, dia) }

    private fun <T> await(
        failure: String,
        probe: () -> T?,
    ): T {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            probe()?.let { return it }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("$failure en $DEADLINE_SECONDS s")
    }

    private fun readRow(
        planId: UUID,
        alumnoId: UUID,
        dia: LocalDate,
    ): Map<String, Any?>? =
        jdbc
            .queryForList(
                "SELECT * FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ? AND alumno_id = ? AND dia = ?",
                planId,
                alumnoId,
                dia,
            ).firstOrNull()

    private fun countRows(planId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE plan_id = ?",
            Int::class.java,
            planId,
        ) ?: 0

    private fun countRowsForStudentAndDay(
        alumnoId: UUID,
        dia: LocalDate,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ? AND dia = ?",
            Int::class.java,
            alumnoId,
            dia,
        ) ?: 0

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SETTLE_MILLIS = 500L
    }
}

private fun session(
    dia: String,
    tipo: String,
    ritmoTipo: String? = null,
    ritmoSegundosPorKm: Int? = null,
    ritmoReferencia: String? = null,
) = PublishedSession(
    dia = LocalDate.parse(dia),
    tipo = tipo,
    volumenTipo = null,
    volumenMetros = null,
    volumenMinutos = null,
    ritmoTipo = ritmoTipo,
    ritmoSegundosPorKm = ritmoSegundosPorKm,
    ritmoReferencia = ritmoReferencia,
    ritmoDeltaSegundosPorKm = null,
    notas = null,
)

private fun planPublicado(
    planId: UUID = UUID.randomUUID(),
    clubId: UUID = UUID.randomUUID(),
    students: List<UUID> = listOf(UUID.randomUUID()),
    sessions: List<PublishedSession> = listOf(session("2026-08-17", "RODAJE")),
) = PlanPublicado(
    eventId = UUID.randomUUID(),
    aggregateId = planId,
    occurredAt = Instant.parse("2026-08-13T10:00:00Z"),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    grupoId = UUID.randomUUID(),
    snapshotAlumnos = students,
    sesiones = sessions,
)
