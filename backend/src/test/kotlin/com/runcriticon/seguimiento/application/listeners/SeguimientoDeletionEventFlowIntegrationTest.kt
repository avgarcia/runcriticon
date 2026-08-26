package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Flujo completo del derecho de supresión (LAL-29): un alumno con filas en `plan_resuelto_por_alumno` las pierde
 * físicamente al consumirse `AlumnoEliminado`. Mismo patrón que
 * `StudentDeletionEventFlowIntegrationTest`/`ResolvedPlanProjectionEventFlowIntegrationTest`.
 */
class SeguimientoDeletionEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `un alumno eliminado pierde sus filas de plan resuelto`() {
        val studentId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        seedRow(studentId, clubId)

        publish(alumnoEliminado(studentId, clubId))

        awaitZeroRows(studentId)
    }

    @Test
    fun `un alumno eliminado pierde tambien sus reportes de sesion, borrado fisico`() {
        val studentId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        val planId = seedRow(studentId, clubId)
        seedReport(studentId, planId)

        publish(alumnoEliminado(studentId, clubId))

        awaitZeroRows(studentId)
        awaitZeroReports(studentId)
    }

    @Test
    fun `un alumno eliminado pierde tambien su fila de consentimiento, borrado fisico`() {
        val studentId = UUID.randomUUID()
        val clubId = UUID.randomUUID()
        seedConsent(studentId, clubId)

        publish(alumnoEliminado(studentId, clubId))

        awaitZeroConsentRows(studentId)
    }

    @Test
    fun `un alumno sin filas proyectadas no falla al eliminarse`() {
        publish(alumnoEliminado(UUID.randomUUID(), UUID.randomUUID()))

        Thread.sleep(SETTLE_MILLIS)
        // No hay aserción de negocio más allá de "no lanza": ausencia de filas es el estado esperado desde el
        // principio.
    }

    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    /** Inserta una fila de plan resuelto y devuelve el `plan_id` usado, para anclar un reporte a la misma clave. */
    private fun seedRow(
        studentId: UUID,
        clubId: UUID,
    ): UUID {
        val planId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, mensaje_al_alumno, es_personalizada,
                 last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, '{"tipo":"RODAJE"}'::jsonb, NULL, FALSE, ?, ?)
            """.trimIndent(),
            studentId,
            planId,
            clubId,
            LocalDate.parse("2026-08-17"),
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
        return planId
    }

    private fun seedReport(
        studentId: UUID,
        planId: UUID,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.reporte_sesion
                (alumno_id, plan_id, dia, club_id, estado, valoracion, reportado_en, actualizado_en)
            VALUES (?, ?, ?, ?, 'HECHO', 4, now(), now())
            """.trimIndent(),
            studentId,
            planId,
            LocalDate.parse("2026-08-17"),
            UUID.randomUUID(),
        )
    }

    private fun seedConsent(
        studentId: UUID,
        clubId: UUID,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.consentimiento_alumno
                (alumno_id, club_id, vigente, version_texto, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, TRUE, 'v2026-08-25', ?, now())
            """.trimIndent(),
            studentId,
            clubId,
            UUID.randomUUID(),
        )
    }

    private fun awaitZeroConsentRows(studentId: UUID) {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            if (countConsentRows(studentId) == 0) return
            Thread.sleep(POLL_MILLIS)
        }
        countConsentRows(studentId) shouldBe 0
    }

    private fun countConsentRows(studentId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?",
            Int::class.java,
            studentId,
        ) ?: 0

    private fun awaitZeroRows(studentId: UUID) {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            if (countRows(studentId) == 0) return
            Thread.sleep(POLL_MILLIS)
        }
        countRows(studentId) shouldBe 0
    }

    private fun awaitZeroReports(studentId: UUID) {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            if (countReports(studentId) == 0) return
            Thread.sleep(POLL_MILLIS)
        }
        countReports(studentId) shouldBe 0
    }

    private fun countRows(studentId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?",
            Int::class.java,
            studentId,
        ) ?: 0

    private fun countReports(studentId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.reporte_sesion WHERE alumno_id = ?",
            Int::class.java,
            studentId,
        ) ?: 0

    private fun alumnoEliminado(
        studentId: UUID,
        clubId: UUID,
    ) = AlumnoEliminado(
        eventId = UUID.randomUUID(),
        aggregateId = studentId,
        occurredAt = Instant.now(),
        clubId = clubId,
        actorId = null,
        traceparent = null,
    )

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SETTLE_MILLIS = 500L
    }
}
