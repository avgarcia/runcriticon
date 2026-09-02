package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.api.events.MarcaRetirada
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
 * Flujo completo de extremo a extremo (LAL-32): publicar `MarcaActualizada`/`MarcaRetirada` en la transacción
 * de un caso de uso, dejar que el outbox lo entregue, y comprobar el recálculo real de
 * `plan_resuelto_por_alumno` — incluida la parte que no puede verificar `MarkPaceRecalculationListenerTest`
 * (unitario, con dobles): que el `UPDATE` toca **solo** las columnas `ritmo_*`, nunca `sesion_resuelta`,
 * `es_personalizada` ni `last_processed_event_*` (ver KDoc de `ResolvedPlanProjection.recalculateRelativePaces`).
 *
 * Las filas de `plan_resuelto_por_alumno` y de `seguimiento.marca_alumno` se siembran con SQL directo, no vía
 * sus propios casos de uso: aísla este flujo del de `RecordMarkCommand`/`ResolvedPlanProjectionListener,
 * mismo criterio que `ResolvedPlanProjectionEventFlowIntegrationTest`.
 */
class MarkPaceRecalculationEventFlowIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `MarcaActualizada resuelve las filas relativas de esa distancia y ese alumno`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val planId = UUID.randomUUID()
        insertResolvedRow(clubId, alumno, planId, dia = "2026-08-19", ritmoReferencia = "10K", ritmoDelta = 10)
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400) // 240 s/km

        publish(marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_calculado_seg_por_km"] != null }
        row["ritmo_calculado_seg_por_km"] shouldBe 250
        row["ritmo_referencia_distancia"] shouldBe "10K"
        row["ritmo_falta_marca"] shouldBe null
    }

    @Test
    fun `MarcaActualizada solo resuelve las filas de esa distancia, no las de otra`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val plan10k = UUID.randomUUID()
        val plan21k = UUID.randomUUID()
        insertResolvedRow(clubId, alumno, plan10k, dia = "2026-08-19", ritmoReferencia = "10K", ritmoDelta = 10)
        insertResolvedRow(clubId, alumno, plan21k, dia = "2026-08-20", ritmoReferencia = "21K", ritmoDelta = 5)
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400)

        publish(marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400))
        awaitRow(plan10k, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_calculado_seg_por_km"] != null }

        val filaOtraDistancia = readRow(plan21k, alumno, LocalDate.parse("2026-08-20"))
        filaOtraDistancia?.get("ritmo_calculado_seg_por_km") shouldBe null
        filaOtraDistancia?.get("ritmo_falta_marca") shouldBe "21K"
    }

    @Test
    fun `MarcaActualizada no toca filas de otro alumno ni de otro club`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val otroAlumno = UUID.randomUUID()
        val otroClub = UUID.randomUUID()
        val planOtroAlumno = UUID.randomUUID()
        val planOtroClub = UUID.randomUUID()
        insertResolvedRow(
            clubId,
            otroAlumno,
            planOtroAlumno,
            dia = "2026-08-19",
            ritmoReferencia = "10K",
            ritmoDelta = 10,
        )
        insertResolvedRow(otroClub, alumno, planOtroClub, dia = "2026-08-19", ritmoReferencia = "10K", ritmoDelta = 10)
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400)

        publish(marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400))
        Thread.sleep(SETTLE_MILLIS)

        val filaOtroAlumno = readRow(planOtroAlumno, otroAlumno, LocalDate.parse("2026-08-19"))
        filaOtroAlumno?.get("ritmo_calculado_seg_por_km") shouldBe null
        val filaOtroClub = readRow(planOtroClub, alumno, LocalDate.parse("2026-08-19"))
        filaOtroClub?.get("ritmo_calculado_seg_por_km") shouldBe null
    }

    @Test
    fun `MarcaRetirada devuelve la fila a falta de marca`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val planId = UUID.randomUUID()
        insertResolvedRow(
            clubId,
            alumno,
            planId,
            dia = "2026-08-19",
            ritmoReferencia = "10K",
            ritmoDelta = 10,
            ritmoCalculado = 250,
        )

        publish(marcaRetirada(alumnoId = alumno, clubId = clubId, distancia = "10K"))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_falta_marca"] != null }
        row["ritmo_calculado_seg_por_km"] shouldBe null
        row["ritmo_referencia_distancia"] shouldBe null
        row["ritmo_falta_marca"] shouldBe "10K"
    }

    @Test
    fun `el recalculo por marca conserva el override y es_personalizada de una fila personalizada`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val planId = UUID.randomUUID()
        insertResolvedRow(
            clubId,
            alumno,
            planId,
            dia = "2026-08-19",
            ritmoReferencia = "10K",
            ritmoDelta = 10,
            esPersonalizada = true,
            mensajeAlAlumno = "Hoy con cuidado",
        )
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400)

        publish(marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_calculado_seg_por_km"] != null }
        row["es_personalizada"] shouldBe true
        row["mensaje_al_alumno"] shouldBe "Hoy con cuidado"
    }

    @Test
    fun `el recalculo por marca no mueve last_processed_event_ts`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val planId = UUID.randomUUID()
        val timestampOriginal = Instant.parse("2026-08-13T10:00:00Z")
        insertResolvedRow(
            clubId,
            alumno,
            planId,
            dia = "2026-08-19",
            ritmoReferencia = "10K",
            ritmoDelta = 10,
            occurredAt = timestampOriginal,
        )
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400)

        publish(marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400))

        val row = awaitRow(planId, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_calculado_seg_por_km"] != null }
        (row["last_processed_event_ts"] as Timestamp).toInstant() shouldBe timestampOriginal
    }

    @Test
    fun `reentregar el mismo MarcaActualizada no rompe nada, idempotencia por event_id`() {
        val clubId = UUID.randomUUID()
        val alumno = UUID.randomUUID()
        val planId = UUID.randomUUID()
        insertResolvedRow(clubId, alumno, planId, dia = "2026-08-19", ritmoReferencia = "10K", ritmoDelta = 10)
        insertMark(clubId, alumno, distancia = "10K", tiempoSegundos = 2_400)
        val event = marcaActualizada(alumnoId = alumno, clubId = clubId, distancia = "10K", tiempoSegundos = 2_400)

        publish(event)
        awaitRow(planId, alumno, LocalDate.parse("2026-08-19")) { it["ritmo_calculado_seg_por_km"] != null }
        publish(event)

        Thread.sleep(SETTLE_MILLIS)
        countProcessed(event.eventId) shouldBe 1
    }

    /** Publica dentro de una transacción: `@ApplicationModuleListener` solo entrega tras un commit. */
    private fun publish(event: IntegrationEvent) {
        transactions.executeWithoutResult { events.publishEvent(event) }
    }

    private fun awaitRow(
        planId: UUID,
        alumnoId: UUID,
        dia: LocalDate,
        readyWhen: (Map<String, Any?>) -> Boolean,
    ): Map<String, Any?> {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(DEADLINE_SECONDS).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            readRow(planId, alumnoId, dia)?.let { row -> if (readyWhen(row)) return row }
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("no se recalculó la fila de $alumnoId/$dia en $DEADLINE_SECONDS s")
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

    private fun countProcessed(eventId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM seguimiento.evento_procesado WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    private fun insertMark(
        clubId: UUID,
        alumnoId: UUID,
        distancia: String,
        tiempoSegundos: Int,
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.marca_alumno (alumno_id, distancia, tiempo_segundos, club_id, modificado_en)
            VALUES (?, ?, ?, ?, now())
            """.trimIndent(),
            alumnoId,
            distancia,
            tiempoSegundos,
            clubId,
        )
    }

    private fun insertResolvedRow(
        clubId: UUID,
        alumnoId: UUID,
        planId: UUID,
        dia: String,
        ritmoReferencia: String,
        ritmoDelta: Int,
        ritmoCalculado: Int? = null,
        esPersonalizada: Boolean = false,
        mensajeAlAlumno: String? = null,
        occurredAt: Instant = Instant.parse("2026-08-13T10:00:00Z"),
    ) {
        // ritmo_falta_marca lleva la referencia salvo que la fila ya venga resuelta (ritmoCalculado != null),
        // en cuyo caso es ritmo_referencia_distancia la que la lleva — mismo invariante mutuamente excluyente
        // que produce el listener real (ver `ResolvedPlanProjectionJdbc.referenceDistanceOrNull`/`missingMarkOrNull`).
        val referenciaResuelta = if (ritmoCalculado != null) ritmoReferencia else null
        val faltaMarca = if (ritmoCalculado != null) null else ritmoReferencia
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, ritmo_tipo_origen, ritmo_calculado_seg_por_km,
                 ritmo_referencia_distancia, ritmo_falta_marca, ritmo_delta_seg_por_km, mensaje_al_alumno,
                 es_personalizada, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, '{"tipo":"TEMPO"}'::jsonb, 'RELATIVO', ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            alumnoId,
            planId,
            clubId,
            LocalDate.parse(dia),
            ritmoCalculado,
            referenciaResuelta,
            faltaMarca,
            ritmoDelta,
            mensajeAlAlumno,
            esPersonalizada,
            UUID.randomUUID(),
            Timestamp.from(occurredAt),
        )
    }

    private companion object {
        const val DEADLINE_SECONDS = 5L
        const val POLL_MILLIS = 25L
        const val SETTLE_MILLIS = 500L
    }
}

private fun marcaActualizada(
    alumnoId: UUID,
    clubId: UUID,
    distancia: String,
    tiempoSegundos: Int,
) = MarcaActualizada(
    eventId = UUID.randomUUID(),
    aggregateId = alumnoId,
    occurredAt = Instant.now(),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    distancia = distancia,
    tiempoSegundos = tiempoSegundos,
)

private fun marcaRetirada(
    alumnoId: UUID,
    clubId: UUID,
    distancia: String,
) = MarcaRetirada(
    eventId = UUID.randomUUID(),
    aggregateId = alumnoId,
    occurredAt = Instant.now(),
    clubId = clubId,
    actorId = null,
    traceparent = null,
    distancia = distancia,
)
